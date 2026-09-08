package com.cloudai.core.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 消息 — 表示对话中的一条消息。
 *
 * <p>支持四种角色：system、user、assistant、tool。推荐使用静态工厂方法创建：</p>
 *
 * <pre>{@code
 * Message.user("Hello");
 * Message.system("You are a helpful assistant");
 * Message.assistant("Hi there!");
 * Message.assistant("", List.of(toolCall));
 * Message.tool("call_123", "result");
 * }</pre>
 *
 * @param role      角色（system / user / assistant / tool）
 * @param content   消息内容
 * @param toolCalls 工具调用列表（role=assistant 时使用）
 * @param name      可选名称
 * @param toolCallId 工具调用 ID（role=tool 时使用）
 */
public record Message(String role, @Nullable String content, @Nullable List<ToolCall> toolCalls,
                       @Nullable String name, @Nullable String toolCallId) {
    public Message {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    // ==================== 角色判断 ====================

    /** 是否为用户消息 */
    public boolean isUser() { return "user".equals(role); }

    /** 是否为系统消息 */
    public boolean isSystem() { return "system".equals(role); }

    /** 是否为助手消息 */
    public boolean isAssistant() { return "assistant".equals(role); }

    /** 是否为工具结果消息 */
    public boolean isTool() { return "tool".equals(role); }

    // ==================== 静态工厂方法 ====================

    public static Message system(String content) {
        return new Message("system", content, null, null, null);
    }

    public static Message user(String content) {
        return new Message("user", content, null, null, null);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content, null, null, null);
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message("assistant", content, toolCalls, null, null);
    }

    public static Message tool(String toolCallId, String content) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId must not be blank for tool messages");
        }
        return new Message("tool", content, null, null, toolCallId);
    }

    /**
     * 创建带名称的消息（用于多用户场景）。
     */
    public static Message user(String content, String name) {
        return new Message("user", content, null, name, null);
    }

    /**
     * 创建带名称的工具结果消息。
     */
    public static Message tool(String toolCallId, String name, String content) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("toolCallId must not be blank for tool messages");
        }
        return new Message("tool", content, null, name, toolCallId);
    }
}