package com.cloudai.core.model;

/**
 * 模型响应结束原因。
 *
 * @author cloud-ai
 * @since 1.0
 */
public enum FinishReason {
    /** 自然结束 */
    STOP,
    /** 达到最大长度 */
    LENGTH,
    /** 需要工具调用 */
    TOOL_CALLS,
    /** 内容过滤触发 */
    CONTENT_FILTER,
    /** 调用过程出错 */
    ERROR,
    /** provider 返回了无法识别的值 */
    UNKNOWN
}
