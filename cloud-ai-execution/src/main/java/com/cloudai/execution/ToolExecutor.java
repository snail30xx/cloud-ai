package com.cloudai.execution;

import com.cloudai.core.tool.ToolCall;
import com.cloudai.execution.ToolResult;

/**
 * 工具执行器 SPI — 执行单个工具调用。
 *
 * <p>每个工具（如 file_read、shell_exec）实现此接口，
 * 注册到 {@link ToolRegistry} 供运行时发现和调用。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * 执行工具调用。
     *
     * @param toolCall 工具调用（包含名称和 JSON 参数）
     * @return 执行结果
     */
    ToolResult execute(ToolCall toolCall);
}
