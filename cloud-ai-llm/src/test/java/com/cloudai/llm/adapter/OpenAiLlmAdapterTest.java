package com.cloudai.llm.adapter;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.Message;
import com.cloudai.core.chat.ModelInfo;
import com.cloudai.core.chat.ModelOptions;
import com.cloudai.core.tool.ToolCall;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.llm.model.OpenAiChatResponse;
import com.cloudai.llm.model.OpenAiChoice;
import com.cloudai.llm.model.OpenAiResponseMessage;
import com.cloudai.llm.model.OpenAiToolCall;
import com.cloudai.llm.model.OpenAiUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAiLlmAdapter 单元测试。
 */
class OpenAiLlmAdapterTest {

    private ProviderProperties props;

    @BeforeEach
    void setUp() {
        props = new ProviderProperties(
                "https://api.openai.com/v1", "test-key", "gpt-4o",
                Duration.ofSeconds(30), 128_000, null, List.of("chat", "tool_calling")
        );
    }

    @Nested
    @DisplayName("ModelInfo")
    class ModelInfoTests {

        @Test
        @DisplayName("should return model info from provider properties")
        void shouldReturnModelInfoFromProviderProperties() {
            var adapter = new OpenAiLlmAdapter(props);
            ModelInfo info = adapter.getModelInfo();

            assertEquals("openai", info.provider());
            assertEquals("gpt-4o", info.model());
            assertEquals(128_000, info.maxTokens());
            assertEquals(List.of("chat", "tool_calling"), info.capabilities());
        }
    }

    @Nested
    @DisplayName("Response parsing")
    class ResponseParsingTests {

        @Test
        @DisplayName("should parse text content response")
        void shouldParseTextContentResponse() {
            var adapter = new OpenAiLlmAdapter(props);
            var response = buildResponse("Hello, world!", null, "stop",
                    new OpenAiUsage(100, 50, 150));

            var result = adapter.parseResponseForTest(response);

            assertEquals("Hello, world!", result.content());
            assertTrue(result.toolCalls().isEmpty());
            assertEquals(FinishReason.STOP, result.finishReason());
            assertEquals(100, result.usage().inputTokens());
            assertEquals(50, result.usage().outputTokens());
            assertEquals(150, result.usage().totalTokens());
        }

        @Test
        @DisplayName("should parse tool calls response")
        void shouldParseToolCallsResponse() {
            var adapter = new OpenAiLlmAdapter(props);
            var toolCalls = List.of(
                    new OpenAiToolCall("call_1", "function",
                            new OpenAiToolCall.OpenAiFunctionCall("get_weather", "{\"city\":\"Beijing\"}")),
                    new OpenAiToolCall("call_2", "function",
                            new OpenAiToolCall.OpenAiFunctionCall("search", "{\"query\":\"test\"}"))
            );
            var response = buildResponse(null, toolCalls, "tool_calls",
                    new OpenAiUsage(200, 100, 300));

            var result = adapter.parseResponseForTest(response);

            assertEquals("", result.content());
            assertEquals(2, result.toolCalls().size());
            assertEquals("call_1", result.toolCalls().get(0).id());
            assertEquals("get_weather", result.toolCalls().get(0).name());
            assertEquals("{\"city\":\"Beijing\"}", result.toolCalls().get(0).arguments());
            assertEquals(FinishReason.TOOL_CALLS, result.finishReason());
        }

        @Test
        @DisplayName("should handle null response")
        void shouldHandleNullResponse() {
            var adapter = new OpenAiLlmAdapter(props);

            var result = adapter.parseResponseForTest(null);

            assertEquals("", result.content());
            assertTrue(result.toolCalls().isEmpty());
            assertEquals(FinishReason.STOP, result.finishReason());
        }

        @Test
        @DisplayName("should handle empty choices")
        void shouldHandleEmptyChoices() {
            var adapter = new OpenAiLlmAdapter(props);
            var response = new OpenAiChatResponse("id", "chat.completion", 0L,
                    "gpt-4o", List.of(), null);

            var result = adapter.parseResponseForTest(response);

            assertEquals("", result.content());
            assertEquals(FinishReason.STOP, result.finishReason());
        }
    }

    @Nested
    @DisplayName("Request building")
    class RequestBuildingTests {

        @Test
        @DisplayName("should use request model when provided in options")
        void shouldUseRequestModelWhenProvided() {
            var adapter = new OpenAiLlmAdapter(props);
            var request = new ChatRequest(
                    List.of(Message.user("Hi")),
                    null,
                    new ModelOptions("gpt-4-turbo", null, null, null, null)
            );

            var body = adapter.buildRequestBodyForTest(request);

            assertEquals("gpt-4-turbo", body.model());
        }

        @Test
        @DisplayName("should fallback to adapter model when options model is null")
        void shouldFallbackToAdapterModel() {
            var adapter = new OpenAiLlmAdapter(props);
            var request = new ChatRequest(
                    List.of(Message.user("Hi")),
                    null,
                    ModelOptions.DEFAULT
            );

            var body = adapter.buildRequestBodyForTest(request);

            assertEquals("gpt-4o", body.model());
        }

        @Test
        @DisplayName("should serialize assistant tool calls")
        void shouldSerializeAssistantToolCalls() {
            var adapter = new OpenAiLlmAdapter(props);
            var toolCalls = List.of(
                    new ToolCall("call_1", "get_weather", "{\"city\":\"Beijing\"}")
            );
            var request = new ChatRequest(
                    List.of(Message.assistant("Let me check", toolCalls)),
                    null,
                    ModelOptions.DEFAULT
            );

            var body = adapter.buildRequestBodyForTest(request);
            var msg = body.messages().get(0);

            assertEquals("assistant", msg.role());
            assertEquals("Let me check", msg.content());
            assertNotNull(msg.toolCalls());
            assertEquals(1, msg.toolCalls().size());
            assertEquals("call_1", msg.toolCalls().get(0).id());
            assertEquals("get_weather", msg.toolCalls().get(0).function().name());
        }

        @Test
        @DisplayName("should serialize tools")
        void shouldSerializeTools() {
            var adapter = new OpenAiLlmAdapter(props);
            var tools = List.of(
                    new ToolDefinition("get_weather", "Get weather", java.util.Map.of("city", "string"))
            );
            var request = new ChatRequest(
                    List.of(Message.user("What's the weather?")),
                    tools,
                    ModelOptions.DEFAULT
            );

            var body = adapter.buildRequestBodyForTest(request);

            assertNotNull(body.tools());
            assertEquals(1, body.tools().size());
            assertEquals("function", body.tools().get(0).type());
            assertEquals("get_weather", body.tools().get(0).function().name());
        }
    }

    @Nested
    @DisplayName("Finish reason mapping")
    class FinishReasonMappingTests {

        @ParameterizedTest
        @CsvSource({
                "stop,           STOP",
                "length,         LENGTH",
                "tool_calls,     TOOL_CALLS",
                "content_filter, CONTENT_FILTER",
                ",               STOP",
                "something_else, UNKNOWN"
        })
        @DisplayName("should map finish reason correctly")
        void shouldMapFinishReason(String input, FinishReason expected) {
            assertEquals(expected, new OpenAiLlmAdapter(props).parseFinishReasonForTest(
                    "".equals(input) ? null : input));
        }
    }

    @Nested
    @DisplayName("Provider validation")
    class ProviderValidationTests {

        @ParameterizedTest
        @CsvSource({
                "'  ',   key,    model,  https://api.openai.com/v1, 30,  baseUrl-blank",
                "https://api.openai.com/v1, '',   model,  https://api.openai.com/v1, 30,  apiKey-blank",
                "https://api.openai.com/v1, key,  '  ',   https://api.openai.com/v1, 30,  model-blank",
                "http://api.openai.com/v1,  key,  model,  https://api.openai.com/v1, 30,  baseUrl-not-https",
        })
        @DisplayName("should reject invalid provider config")
        void shouldRejectInvalidProviderConfig(String baseUrl, String apiKey, String model,
                                                String validUrl, int timeoutSec, String scenario) {
            var invalidProps = new ProviderProperties(
                    baseUrl, apiKey, model, Duration.ofSeconds(timeoutSec), 128_000, null, List.of()
            );
            assertThrows(IllegalStateException.class, invalidProps::validate);
        }
    }

    // ==================== 测试辅助方法 ====================

    private static OpenAiChatResponse buildResponse(String content, List<OpenAiToolCall> toolCalls,
                                                     String finishReason, OpenAiUsage usage) {
        var message = new OpenAiResponseMessage("assistant", content, toolCalls);
        var choice = new OpenAiChoice(0, null, message, finishReason);
        return new OpenAiChatResponse("test-id", "chat.completion", 1234567890L,
                "gpt-4o", List.of(choice), usage);
    }
}