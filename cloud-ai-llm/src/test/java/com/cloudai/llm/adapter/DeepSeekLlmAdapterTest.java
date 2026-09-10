package com.cloudai.llm.adapter;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.Message;
import com.cloudai.core.chat.ModelInfo;
import com.cloudai.core.chat.ModelOptions;
import com.cloudai.llm.config.ProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeepSeekLlmAdapter 单元测试。
 */
class DeepSeekLlmAdapterTest {

    private ProviderProperties thinkingProps;
    private ProviderProperties noThinkingProps;

    @BeforeEach
    void setUp() {
        thinkingProps = new ProviderProperties(
                "https://api.deepseek.com", "sk-test-key", "deepseek-v4-pro",
                Duration.ofSeconds(120), 128_000, null,
                List.of("chat", "tool_calling", "thinking")
        );
        noThinkingProps = new ProviderProperties(
                "https://api.deepseek.com", "sk-test-key", "deepseek-v4-pro",
                Duration.ofSeconds(120), 128_000, null,
                List.of("chat", "tool_calling")
        );
    }

    @Nested
    @DisplayName("ModelInfo")
    class ModelInfoTests {

        @Test
        @DisplayName("should return model info with provider name and capabilities")
        void shouldReturnModelInfo() {
            var adapter = new DeepSeekLlmAdapter(thinkingProps);
            var info = adapter.getModelInfo();

            assertEquals("deepseek", info.provider());
            assertTrue(info.capabilities().contains("thinking"));
        }
    }

    @Nested
    @DisplayName("Request building — thinking mode")
    class ThinkingModeTests {

        @Test
        @DisplayName("should include reasoning_effort and thinking when capabilities has thinking")
        void shouldIncludeReasoningEffortAndThinking() {
            var adapter = new DeepSeekLlmAdapter(thinkingProps);
            var request = new ChatRequest(
                    List.of(Message.user("Explain quantum computing")),
                    null,
                    ModelOptions.DEFAULT
            );

            var body = adapter.buildRequestBodyForTest(request);

            assertEquals("high", body.reasoningEffort());
            assertNotNull(body.thinking());
            assertEquals("enabled", body.thinking().type());
        }

        @Test
        @DisplayName("should not include reasoning_effort when capabilities lacks thinking")
        void shouldNotIncludeReasoningEffortWhenNoThinking() {
            var adapter = new DeepSeekLlmAdapter(noThinkingProps);
            var request = new ChatRequest(
                    List.of(Message.user("Hi")),
                    null,
                    ModelOptions.DEFAULT
            );

            var body = adapter.buildRequestBodyForTest(request);

            assertNull(body.reasoningEffort());
            assertNull(body.thinking());
        }
    }

    @Nested
    @DisplayName("Response parsing")
    class ResponseParsingTests {

        @Test
        @DisplayName("should parse text content (inherited from base)")
        void shouldParseTextContent() {
            var adapter = new DeepSeekLlmAdapter(thinkingProps);
            var response = buildResponse("DeepSeek response", null, "stop");

            var result = adapter.parseResponseForTest(response);

            assertEquals("DeepSeek response", result.content());
            assertEquals(FinishReason.STOP, result.finishReason());
        }

        @Test
        @DisplayName("should parse tool calls (inherited from base)")
        void shouldParseToolCalls() {
            var adapter = new DeepSeekLlmAdapter(thinkingProps);
            var toolCalls = List.of(
                    new com.cloudai.llm.model.OpenAiToolCall("call_1", "function",
                            new com.cloudai.llm.model.OpenAiToolCall.OpenAiFunctionCall(
                                    "search", "{\"query\":\"AI\"}"))
            );
            var response = buildResponseWithToolCalls(toolCalls, "tool_calls");

            var result = adapter.parseResponseForTest(response);

            assertEquals(1, result.toolCalls().size());
            assertEquals("call_1", result.toolCalls().get(0).id());
            assertEquals("search", result.toolCalls().get(0).name());
            assertEquals(FinishReason.TOOL_CALLS, result.finishReason());
        }
    }

    @Nested
    @DisplayName("Request building — model override")
    class ModelOverrideTests {

        @Test
        @DisplayName("should use request model override")
        void shouldUseRequestModelOverride() {
            var adapter = new DeepSeekLlmAdapter(thinkingProps);
            var request = new ChatRequest(
                    List.of(Message.user("Hi")),
                    null,
                    new ModelOptions("deepseek-v4-flash", null, null, null, null)
            );

            var body = adapter.buildRequestBodyForTest(request);

            assertEquals("deepseek-v4-flash", body.model());
        }
    }

    // ==================== 测试辅助方法 ====================

    private static com.cloudai.llm.model.OpenAiChatResponse buildResponse(
            String content, List<com.cloudai.llm.model.OpenAiToolCall> toolCalls, String finishReason) {
        var message = new com.cloudai.llm.model.OpenAiResponseMessage("assistant", content, toolCalls);
        var choice = new com.cloudai.llm.model.OpenAiChoice(0, null, message, finishReason);
        return new com.cloudai.llm.model.OpenAiChatResponse(
                "test-id", "chat.completion", 1234567890L,
                "deepseek-v4-pro", List.of(choice),
                new com.cloudai.llm.model.OpenAiUsage(100, 50, 150));
    }

    private static com.cloudai.llm.model.OpenAiChatResponse buildResponseWithToolCalls(
            List<com.cloudai.llm.model.OpenAiToolCall> toolCalls, String finishReason) {
        return buildResponse(null, toolCalls, finishReason);
    }
}