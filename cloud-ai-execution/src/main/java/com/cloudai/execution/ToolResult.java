package com.cloudai.execution;

import org.jspecify.annotations.Nullable;

/**
 * 工具执行结果。
 *
 * @param toolCallId 对应的 ToolCall ID
 * @param success    是否成功
 * @param output     执行输出（成功时的结果内容）
 * @param error      错误信息（失败时），null 表示无错误
 * @author cloud-ai
 * @since 1.0
 */
public record ToolResult(
        String toolCallId,
        boolean success,
        String output,
        @Nullable String error) {

    public ToolResult {
        if (toolCallId == null) toolCallId = "";
        output = output != null ? output : "";
    }

    /** 成功结果。 */
    public static ToolResult success(String toolCallId, String output) {
        return new ToolResult(toolCallId, true, output, null);
    }

    /** 失败结果。 */
    public static ToolResult failure(String toolCallId, String error) {
        return new ToolResult(toolCallId, false, "", error);
    }
}
