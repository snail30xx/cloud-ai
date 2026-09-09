package com.cloudai.security.model;

/**
 * 操作类型 — 定义 Agent 可执行的操作类别。
 *
 * @author cloud-ai
 * @since 1.0
 */
public enum OperationType {
    /** 文件读取 */
    FILE_READ,
    /** 文件写入 */
    FILE_WRITE,
    /** 文件删除 */
    FILE_DELETE,
    /** Shell 命令执行 */
    SHELL_EXEC,
    /** 网络调用 */
    NETWORK_CALL,
    /** 自定义操作 */
    CUSTOM;

    /**
     * 根据工具名推断操作类型。
     *
     * @param toolName 工具名（如 "file_read"、"shell_exec"）
     * @return 匹配的操作类型，无法识别时返回 {@link #CUSTOM}
     */
    public static OperationType from(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return CUSTOM;
        }
        var lower = toolName.toLowerCase();
        if (lower.contains("file_read") || lower.contains("read_file")) return FILE_READ;
        if (lower.contains("file_write") || lower.contains("write_file")) return FILE_WRITE;
        if (lower.contains("file_delete") || lower.contains("delete_file")) return FILE_DELETE;
        if (lower.contains("shell") || lower.contains("exec") || lower.contains("command")) return SHELL_EXEC;
        if (lower.contains("http") || lower.contains("curl") || lower.contains("network")) return NETWORK_CALL;
        return CUSTOM;
    }
}
