package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

/**
 * OpenAI 工具调用（来自响应）。
 *
 * @author cloud-ai
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiToolCall(@Nullable String id, @Nullable String type, @Nullable OpenAiFunctionCall function) {

    /** 工具调用函数 */
    public record OpenAiFunctionCall(@Nullable String name, @Nullable String arguments) {}
}