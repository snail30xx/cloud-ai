package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * OpenAI 响应中的 choice。
 *
 * @author cloud-ai
 * @since 1.0
 */
public record OpenAiChoice(
        int index,
        @Nullable OpenAiResponseMessage delta,
        @Nullable OpenAiResponseMessage message,
        @Nullable @JsonProperty("finish_reason") String finishReason) {

    /** 返回有效消息：流式取 delta，非流式取 message */
    @Nullable
    public OpenAiResponseMessage effectiveMessage() {
        return message != null ? message : delta;
    }
}