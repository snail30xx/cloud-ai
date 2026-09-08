package com.cloudai.llm.client;

import com.cloudai.core.model.*;
import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.advisor.Advisor;
import com.cloudai.llm.client.support.ModelRouterAccessor;
import com.cloudai.llm.stream.MessageAggregator;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatClient 核心流程测试 — 只测真正有行为逻辑的路径。
 */
class ChatClientTest {

    private static class MockRouterAccessor implements ModelRouterAccessor {
        private final List<ChatRequest> capturedRequests = new ArrayList<>();
        private final List<String> capturedProviders = new ArrayList<>();
        private ChatResponse callResponse;
        private Flux<ChatResponse> streamFlux;

        MockRouterAccessor withCallResponse(ChatResponse resp) { this.callResponse = resp; return this; }
        MockRouterAccessor withStreamFlux(Flux<ChatResponse> flux) { this.streamFlux = flux; return this; }

        @Override public ChatResponse chat(String p, ChatRequest r) { capturedProviders.add(p); capturedRequests.add(r); return callResponse; }
        @Override public ChatResponse chatDefault(ChatRequest r) { capturedProviders.add(null); capturedRequests.add(r); return callResponse; }
        @Override public Flux<ChatResponse> stream(String p, ChatRequest r) { capturedProviders.add(p); capturedRequests.add(r); return streamFlux != null ? streamFlux : Flux.empty(); }
        @Override public Flux<ChatResponse> streamDefault(ChatRequest r) { capturedProviders.add(null); capturedRequests.add(r); return streamFlux != null ? streamFlux : Flux.empty(); }

        ChatRequest lastRequest() { return capturedRequests.getLast(); }
        String lastProvider() { return capturedProviders.getLast(); }
    }

    private static ChatResponse textResponse(String content) {
        return ChatResponse.of("resp-1", "gpt-4o", content, List.of(),
                new TokenUsage(10, 20), FinishReason.STOP, java.util.Map.of());
    }

    // ==================== 端到端：call ====================

    @Nested
    @DisplayName("端到端同步调用")
    class CallE2E {

        @Test
        @DisplayName("prompt → call → content")
        void promptCallContent() {
            var router = new ModelRouter("default");
            router.register("default", new StubChatModel("openai", "gpt-4o", "Hello, World!"));
            router.validate();

            var client = ChatClientFactory.create(router);
            var result = client.prompt("Hi").call().content();

            assertEquals("Hello, World!", result);
        }

        @Test
        @DisplayName("指定 provider 路由到对应模型")
        void providerRouting() {
            var router = new ModelRouter("default");
            router.register("default", new StubChatModel("openai", "gpt-4o", "default-resp"));
            router.register("deepseek", new StubChatModel("deepseek", "deepseek-v4", "deepseek-resp"));
            router.validate();

            var client = ChatClientFactory.create(router);
            var defaultResult = client.prompt("Hi").call().content();
            var deepseekResult = client.prompt("Hi").provider("deepseek").call().content();

            assertEquals("default-resp", defaultResult);
            assertEquals("deepseek-resp", deepseekResult);
        }
    }

    // ==================== 端到端：stream ====================

    @Nested
    @DisplayName("端到端流式调用")
    class StreamE2E {

        @Test
        @DisplayName("prompt → stream → aggregate")
        void promptStreamAggregate() {
            var router = new ModelRouter("default");
            router.register("default", new StubStreamingChatModel("openai", "gpt-4o",
                    Flux.just("Hello", " ", "World")));
            router.validate();

            var client = ChatClientFactory.create(router);
            var result = client.prompt("Hi").stream().aggregate().block();

            assertNotNull(result);
            assertEquals("Hello World", result.content());
        }

        @Test
        @DisplayName("指定 provider 流式路由")
        void providerStreamRouting() {
            var router = new ModelRouter("default");
            router.register("default", new StubStreamingChatModel("openai", "gpt-4o",
                    Flux.just("default")));
            router.register("deepseek", new StubStreamingChatModel("deepseek", "deepseek-v4",
                    Flux.just("deepseek")));
            router.validate();

            var client = ChatClientFactory.create(router);
            var defaultResult = client.prompt("Hi").stream().aggregate().block();
            var deepseekResult = client.prompt("Hi").provider("deepseek").stream().aggregate().block();

            assertNotNull(defaultResult);
            assertEquals("default", defaultResult.content());
            assertNotNull(deepseekResult);
            assertEquals("deepseek", deepseekResult.content());
        }
    }

    // ==================== Advisor 链 ====================

    @Nested
    @DisplayName("Advisor 链式拦截")
    class AdvisorChain {

        @Test
        @DisplayName("多个 advisor 按顺序修改请求")
        void multipleAdvisorsModifyRequestInOrder() {
            var mockRouter = new MockRouterAccessor().withCallResponse(textResponse("ok"));
            var advisor1 = (Advisor) (request, chain) -> {
                var modified = new ChatRequest(request.messages(), request.tools(),
                        new ModelOptions("model-from-advisor1", 0.5, null, null, null));
                return chain.next(modified);
            };
            var advisor2 = (Advisor) (request, chain) -> {
                // 能看到 advisor1 修改的 model，在此基础上追加 maxTokens
                var modified = new ChatRequest(request.messages(), request.tools(),
                        new ModelOptions(request.options().model(),
                                request.options().temperature(), 200, null, null));
                return chain.next(modified);
            };

            var client = new ChatClient(mockRouter, ObservationRegistry.NOOP, List.of(advisor1, advisor2));
            client.prompt("Hi").call();

            var req = mockRouter.lastRequest();
            assertEquals("model-from-advisor1", req.options().model());
            assertEquals(0.5, req.options().temperature());
            assertEquals(200, req.options().maxTokens());
        }

        @Test
        @DisplayName("请求时追加 advisor 同样生效")
        void requestTimeAdvisor() {
            var mockRouter = new MockRouterAccessor().withCallResponse(textResponse("ok"));
            var client = new ChatClient(mockRouter, ObservationRegistry.NOOP, List.of());

            client.prompt("Hi").advisors((Advisor) (req, chain) ->
                    chain.next(new ChatRequest(req.messages(), req.tools(),
                            new ModelOptions("runtime-advisor", null, null, null, null)))
            ).call();

            assertEquals("runtime-advisor", mockRouter.lastRequest().options().model());
        }
    }

    // ==================== Builder 扩展 ====================

    @Nested
    @DisplayName("ChatClientBuilder 扩展点")
    class BuilderExtension {

        @Test
        @DisplayName("subclass 覆盖 createClient 注入自定义 ChatClient")
        void subclassOverrideCreateClient() {
            var mockRouter = new MockRouterAccessor().withCallResponse(textResponse("ok"));
            var ref = new AtomicReference<ChatClient>();

            var builder = new ChatClientBuilder(mockRouter) {
                @Override
                protected ChatClient createClient() {
                    var c = super.createClient();
                    ref.set(c);
                    return c;
                }
            };

            var built = builder.build();
            assertNotNull(built);
            assertSame(built, ref.get());
        }
    }

    // ==================== MessageAggregator 工具调用合并 ====================

    @Nested
    @DisplayName("MessageAggregator 流式聚合")
    class MessageAggregatorTests {

        @Test
        @DisplayName("内容拼接 + 首 chunk 的 id/model 保留")
        void contentAggregationAndIdRetention() {
            var chunks = Flux.just(
                    ChatResponse.of("resp-id", "my-model", "Hello", List.of(),
                            new TokenUsage(0, 0), FinishReason.STOP, java.util.Map.of()),
                    ChatResponse.of("ignored", "ignored", " World", List.of(),
                            new TokenUsage(100, 200), FinishReason.STOP, java.util.Map.of())
            );

            StepVerifier.create(MessageAggregator.aggregate(chunks))
                    .assertNext(agg -> {
                        assertEquals("Hello World", agg.content());
                        assertEquals("resp-id", agg.id());
                        assertEquals("my-model", agg.model());
                        assertEquals(100, agg.usage().inputTokens());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("工具调用按索引合并（流式分片传输）")
        void toolCallMergeByIndex() {
            var chunk1 = ChatResponse.of("id", null, "", List.of(
                    new ToolCall("call_1", "get_weather", "{\"city\":\"Beijing\"")
            ), new TokenUsage(0, 0), FinishReason.STOP, java.util.Map.of());

            var chunk2 = ChatResponse.of(null, null, "", List.of(
                    new ToolCall("", "", ", \"unit\":\"celsius\"}")
            ), new TokenUsage(0, 0), FinishReason.STOP, java.util.Map.of());

            StepVerifier.create(MessageAggregator.aggregate(Flux.just(chunk1, chunk2)))
                    .assertNext(agg -> {
                        assertEquals(1, agg.toolCalls().size());
                        var tc = agg.toolCalls().get(0);
                        assertEquals("call_1", tc.id());
                        assertEquals("get_weather", tc.name());
                        assertEquals("{\"city\":\"Beijing\", \"unit\":\"celsius\"}", tc.arguments());
                    })
                    .verifyComplete();
        }
    }

    // ==================== Stub ====================

    private static class StubChatModel implements com.cloudai.core.spi.ChatModel, com.cloudai.core.spi.ModelDiscovery {
        private final ModelInfo info;
        private final String content;

        StubChatModel(String provider, String model, String content) {
            this.info = new ModelInfo(provider, model, 8000, List.of("chat"));
            this.content = content;
        }

        @Override public ChatResponse call(ChatRequest r) { return textResponse(content); }
        @Override public Flux<ChatResponse> stream(ChatRequest r) { return Flux.just(textResponse(content)); }
        @Override public ModelInfo getModelInfo() { return info; }
        @Override public List<ModelInfo> listModels() { return List.of(info); }
    }

    private static class StubStreamingChatModel implements com.cloudai.core.spi.ChatModel, com.cloudai.core.spi.ModelDiscovery {
        private final ModelInfo info;
        private final Flux<String> contents;

        StubStreamingChatModel(String provider, String model, Flux<String> contents) {
            this.info = new ModelInfo(provider, model, 8000, List.of("chat"));
            this.contents = contents;
        }

        @Override public ChatResponse call(ChatRequest r) { return textResponse("stub"); }
        @Override public Flux<ChatResponse> stream(ChatRequest r) {
            return contents.map(c -> ChatResponse.of(c, List.of(), new TokenUsage(0, 0), FinishReason.STOP));
        }
        @Override public ModelInfo getModelInfo() { return info; }
        @Override public List<ModelInfo> listModels() { return List.of(info); }
    }
}