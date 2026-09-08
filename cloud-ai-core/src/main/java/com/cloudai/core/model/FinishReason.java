package com.cloudai.core.model;

/**
 * 模型响应结束原因。
 */
public enum FinishReason {
    STOP,           // 自然结束
    LENGTH,         // 达到最大长度
    TOOL_CALLS,     // 需要工具调用
    CONTENT_FILTER, // 内容过滤
    ERROR,          // 错误
    UNKNOWN         // 未知原因（provider 返回了无法识别的值）
}