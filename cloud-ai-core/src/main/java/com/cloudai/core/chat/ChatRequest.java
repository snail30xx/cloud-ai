package com.cloudai.core.chat;

import com.cloudai.core.tool.ToolDefinition;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 对话请求。
 *
 * @param messages 消息列表
 * @param tools    可用工具定义
 * @param options  模型选项
  * @author cloud-ai
 * @since 1.0
*/
public record ChatRequest(List<Message> messages, @Nullable List<ToolDefinition> tools,
                          @Nullable ModelOptions options) {
    public ChatRequest {
        messages = List.copyOf(messages);
        tools = tools != null ? List.copyOf(tools) : List.of();
    }
}
