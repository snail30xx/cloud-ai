package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI Token 用量。
 *
 * @author cloud-ai
 * @since 1.0
 */
public record OpenAiUsage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens) {
}