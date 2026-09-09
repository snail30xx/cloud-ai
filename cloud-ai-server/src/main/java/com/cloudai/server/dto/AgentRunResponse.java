package com.cloudai.server.dto;

/**
 * Agent 运行响应 DTO。
 *
 * @param traceId          追踪 ID
 * @param content          最终回答文本
 * @param finishStatus     终止状态
 * @param turnsExecuted    LLM 调用轮次
 * @param toolCallsExecuted 工具调用总数
 * @param error            错误信息（成功时为空）
 * @author cloud-ai
 * @since 1.0
 */
public record AgentRunResponse(
        String traceId,
        String content,
        String finishStatus,
        int turnsExecuted,
        int toolCallsExecuted,
        String error) {

    public AgentRunResponse {
        content = content != null ? content : "";
        finishStatus = finishStatus != null ? finishStatus : "";
        error = error != null ? error : "";
    }
}
