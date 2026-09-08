package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * OpenAI 响应消息。
 *
 * @author cloud-ai
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiResponseMessage(
        @Nullable String role,
        @Nullable String content,
        @Nullable @JsonProperty("tool_calls") List<OpenAiToolCall> toolCalls) {
}