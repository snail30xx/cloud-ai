package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 请求体（兼容 OpenAI / DeepSeek / 混元 等协议）。
 *
 * <pre>
 * POST /v1/chat/completions
 * </pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
        @Nullable String model,
        @Nullable List<OpenAiMessage> messages,
        @Nullable Double temperature,
        @Nullable @JsonProperty("max_tokens") Integer maxTokens,
        @Nullable @JsonProperty("top_p") Double topP,
        @Nullable List<String> stop,
        @Nullable List<OpenAiTool> tools,
        @Nullable @JsonProperty("reasoning_effort") String reasoningEffort,
        @Nullable Thinking thinking,
        @Nullable Boolean stream,
        @Nullable Map<String, Object> extraBody) {

    /** 不包含 DeepSeek 特有字段的便捷构造器 */
    public OpenAiChatRequest(
            String model,
            List<OpenAiMessage> messages,
            Double temperature,
            Integer maxTokens,
            Double topP,
            List<String> stop,
            List<OpenAiTool> tools) {
        this(model, messages, temperature, maxTokens, topP, stop, tools, null, null, null, null);
    }

    /** 包含 DeepSeek 字段但不含 stream/extraBody 的便捷构造器 */
    public OpenAiChatRequest(
            String model,
            List<OpenAiMessage> messages,
            Double temperature,
            Integer maxTokens,
            Double topP,
            List<String> stop,
            List<OpenAiTool> tools,
            String reasoningEffort,
            Thinking thinking) {
        this(model, messages, temperature, maxTokens, topP, stop, tools, reasoningEffort, thinking, null, null);
    }

    /** 思考模式配置（DeepSeek 专用） */
    public record Thinking(String type) {}

    /** 创建带 stream=true 的副本 */
    public OpenAiChatRequest withStream(boolean stream) {
        return new OpenAiChatRequest(model, messages, temperature, maxTokens, topP, stop,
                tools, reasoningEffort, thinking, stream, extraBody);
    }

    /** 创建带 reasoningEffort 的副本（DeepSeek 专用） */
    public OpenAiChatRequest withReasoningEffort(String reasoningEffort) {
        return new OpenAiChatRequest(model, messages, temperature, maxTokens, topP, stop,
                tools, reasoningEffort, thinking, stream, extraBody);
    }

    /** 创建带 thinking 的副本（DeepSeek 专用） */
    public OpenAiChatRequest withThinking(Thinking thinking) {
        return new OpenAiChatRequest(model, messages, temperature, maxTokens, topP, stop,
                tools, reasoningEffort, thinking, stream, extraBody);
    }
}