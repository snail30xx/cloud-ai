package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * OpenAI Chat Completions 响应体。
 *
 * @author cloud-ai
 * @since 1.0
 */
public record OpenAiChatResponse(
        @Nullable String id,
        @Nullable String object,
        long created,
        @Nullable String model,
        @Nullable List<OpenAiChoice> choices,
        @Nullable OpenAiUsage usage) {

    /** 判断响应是否有效 */
    public boolean hasChoices() {
        return choices != null && !choices.isEmpty();
    }

    /** 获取第一个 choice */
    @Nullable
    public OpenAiChoice firstChoice() {
        return hasChoices() ? choices.get(0) : null;
    }
}