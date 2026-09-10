package com.cloudai.llm.adapter;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.Message;
import com.cloudai.core.chat.ModelOptions;
import com.cloudai.core.chat.TokenUsage;
import com.cloudai.core.tool.ToolCall;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.llm.config.ProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AnthropicLlmAdapter 协议映射")
class AnthropicAdapterTest {

    private AnthropicLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        var props = new ProviderProperties("https://api.anthropic.com", "sk-test",
                "claude-sonnet-4", Duration.ofSeconds(60), 200_000, null, null);
        adapter = new AnthropicLlmAdapter(props);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildBody(ChatRequest request) {
        return (Map<String, Object>) adapter.buildRequestBodyInternal(request);
    }

    @Nested
    @DisplayName("请求映射")
    class RequestMapping {

        @Test
        @DisplayName("system 消息提取为顶层 system 字段，不进入消息数组")
        void systemLiftedToTopLevel() {
            var request = new ChatRequest(List.of(
                    Message.system("You are helpful."),
                    Message.user("Hi")), null, null);

            var body = buildBody(request);

            assertEquals("You are helpful.", body.get("system"));
            var messages = (List<Map<String, Object>>) body.get("messages");
            assertEquals(1, messages.size());
            assertEquals("user", messages.get(0).get("role"));
        }

        @Test
        @DisplayName("工具定义映射为 tools[].input_schema，无工具时不发送 tools 字段")
        void toolsMappedToInputSchema() {
            var definition = new ToolDefinition("get_weather", "Get weather",
                    Map.of("type", "object", "properties",
                            Map.of("city", Map.of("type", "string")), "required", List.of("city")));

            var withTools = buildBody(new ChatRequest(List.of(Message.user("weather?")),
                    List.of(definition), null));
            var tools = (List<Map<String, Object>>) withTools.get("tools");
            assertEquals(1, tools.size());
            assertEquals("get_weather", tools.get(0).get("name"));
            assertEquals(Map.of("type", "object", "properties",
                    Map.of("city", Map.of("type", "string")), "required", List.of("city")),
                    tools.get(0).get("input_schema"));

            var withoutTools = buildBody(new ChatRequest(List.of(Message.user("hi")), null, null));
            assertFalse(withoutTools.containsKey("tools"), "空工具列表不应发送 tools 字段");
        }

        @Test
        @DisplayName("assistant 工具调用映射为 tool_use 内容块（input 为对象）")
        void assistantToolCallsBecomeToolUseBlocks() {
            var request = new ChatRequest(List.of(
                    Message.user("weather?"),
                    Message.assistant("", List.of(new ToolCall("toolu_1", "get_weather", "{\"city\":\"Beijing\"}"))),
                    Message.tool("toolu_1", "sunny, 25C")), null, null);

            var body = buildBody(request);
            var messages = (List<Map<String, Object>>) body.get("messages");

            assertEquals(3, messages.size());
            assertEquals("assistant", messages.get(1).get("role"));
            var blocks = (List<Map<String, Object>>) messages.get(1).get("content");
            assertEquals("tool_use", blocks.get(0).get("type"));
            assertEquals("toolu_1", blocks.get(0).get("id"));
            assertEquals("get_weather", blocks.get(0).get("name"));
            assertEquals(Map.of("city", "Beijing"), blocks.get(0).get("input"));

            // tool 结果 → user 角色 tool_result 内容块
            assertEquals("user", messages.get(2).get("role"));
            var resultBlocks = (List<Map<String, Object>>) messages.get(2).get("content");
            assertEquals("tool_result", resultBlocks.get(0).get("type"));
            assertEquals("toolu_1", resultBlocks.get(0).get("tool_use_id"));
            assertEquals("sunny, 25C", resultBlocks.get(0).get("content"));
        }

        @Test
        @DisplayName("非法 JSON 参数降级为空 input 对象")
        void invalidArgumentsDegradeToEmptyInput() {
            var request = new ChatRequest(List.of(
                    Message.assistant("", List.of(new ToolCall("toolu_2", "bad_tool", "not-json")))),
                    null, null);

            var body = buildBody(request);
            var messages = (List<Map<String, Object>>) body.get("messages");
            var blocks = (List<Map<String, Object>>) messages.get(0).get("content");
            assertEquals(Map.of(), blocks.get(0).get("input"));
        }

        @Test
        @DisplayName("ModelOptions 映射 temperature/top_p/stop_sequences")
        void modelOptionsMapped() {
            var options = new ModelOptions("claude-sonnet-4", 0.7, 1024, 0.9,
                    List.of("END"), null);
            var body = buildBody(new ChatRequest(List.of(Message.user("hi")), null, options));

            assertEquals(0.7, body.get("temperature"));
            assertEquals(0.9, body.get("top_p"));
            assertEquals(List.of("END"), body.get("stop_sequences"));
            assertEquals(1024, body.get("max_tokens"));
        }
    }

    @Nested
    @DisplayName("响应解析")
    class ResponseParsing {

        @Test
        @DisplayName("tool_use 内容块解析回 ToolCall，input 序列化为 JSON 字符串")
        void toolUseParsedToToolCall() {
            var response = new AnthropicLlmAdapter.AnthropicResponse(
                    "msg_1", "claude-sonnet-4", "assistant", "tool_use",
                    List.of(
                            new AnthropicLlmAdapter.ContentBlock("text", "Let me check.", null, null, null),
                            new AnthropicLlmAdapter.ContentBlock("tool_use", null, "toolu_1", "get_weather",
                                    Map.of("city", "Beijing"))),
                    new AnthropicLlmAdapter.Usage(10, 20));

            var parsed = adapter.parseResponseInternal(response);

            assertEquals("Let me check.", parsed.content());
            assertEquals(1, parsed.toolCalls().size());
            var toolCall = parsed.toolCalls().get(0);
            assertEquals("toolu_1", toolCall.id());
            assertEquals("get_weather", toolCall.name());
            assertEquals("{\"city\":\"Beijing\"}", toolCall.arguments());
            assertEquals(FinishReason.TOOL_CALLS, parsed.finishReason());
            assertEquals(new TokenUsage(10, 20), parsed.usage());
        }

        @Test
        @DisplayName("stop_reason 映射：end_turn→STOP、max_tokens→LENGTH、tool_use→TOOL_CALLS")
        void stopReasonMapping() {
            var endTurn = responseWithStopReason("end_turn");
            var maxTokens = responseWithStopReason("max_tokens");
            var toolUse = responseWithStopReason("tool_use");
            var stopSequence = responseWithStopReason("stop_sequence");

            assertEquals(FinishReason.STOP, endTurn.finishReason());
            assertEquals(FinishReason.LENGTH, maxTokens.finishReason());
            assertEquals(FinishReason.TOOL_CALLS, toolUse.finishReason());
            assertEquals(FinishReason.STOP, stopSequence.finishReason());
        }

        @Test
        @DisplayName("空 content 边界 — 返回空文本且不抛异常")
        void emptyContentBorder() {
            var response = new AnthropicLlmAdapter.AnthropicResponse(
                    "msg_2", "claude-sonnet-4", "assistant", "end_turn",
                    null, null);

            var parsed = adapter.parseResponseInternal(response);

            assertNotNull(parsed);
            assertEquals("", parsed.content());
            assertTrue(parsed.toolCalls().isEmpty());
            assertEquals(new TokenUsage(0, 0), parsed.usage());
        }
    }

    private ChatResponse responseWithStopReason(String stopReason) {
        var response = new AnthropicLlmAdapter.AnthropicResponse(
                "msg_x", "claude-sonnet-4", "assistant", stopReason,
                List.of(new AnthropicLlmAdapter.ContentBlock("text", "ok", null, null, null)),
                null);
        return adapter.parseResponseInternal(response);
    }
}
