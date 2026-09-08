package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * OpenAI 工具定义（发送给 API 的格式）。
 *
 * @author cloud-ai
 * @since 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiTool(@Nullable String type, @Nullable OpenAiFunction function) {

    /** 从内部 ToolDefinition 创建 function 类型工具 */
    public static OpenAiTool from(String name, String description, Map<String, Object> parameters) {
        return new OpenAiTool("function", new OpenAiFunction(name, description, parameters));
    }

    /** 工具函数描述 */
    public record OpenAiFunction(@Nullable String name, @Nullable String description,
                                 @Nullable Map<String, Object> parameters) {}
}