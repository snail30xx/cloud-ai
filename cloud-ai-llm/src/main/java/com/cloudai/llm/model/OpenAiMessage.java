package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * OpenAI 消息格式。
 *
 * @author cloud-ai
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiMessage(
        @Nullable String role,
        @Nullable String content,
        @Nullable String name,
        @Nullable @JsonProperty("tool_call_id") String toolCallId,
        @Nullable @JsonProperty("tool_calls") List<OpenAiToolCall> toolCalls) {

    /** 创建 system 消息 */
    public static OpenAiMessage system(String content) {
        return new OpenAiMessage("system", content, null, null, null);
    }

    /** 创建 user 消息 */
    public static OpenAiMessage user(String content) {
        return new OpenAiMessage("user", content, null, null, null);
    }

    /** 创建 assistant 消息 */
    public static OpenAiMessage assistant(String content) {
        return new OpenAiMessage("assistant", content, null, null, null);
    }

    /** 创建 tool 消息 */
    public static OpenAiMessage tool(String toolCallId, String content) {
        return new OpenAiMessage("tool", content, null, toolCallId, null);
    }
}