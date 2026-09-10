package com.cloudai.core.model;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 工具定义（用于发送给 LLM）。
 *
 * @param name        工具名称
 * @param description 工具描述
 * @param parameters  参数 JSON Schema
  * @author cloud-ai
 * @since 1.0
*/
public record ToolDefinition(String name, String description, @Nullable Map<String, Object> parameters) {
    public ToolDefinition {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}
