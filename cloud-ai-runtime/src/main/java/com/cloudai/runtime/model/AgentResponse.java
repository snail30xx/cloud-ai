package com.cloudai.runtime.model;

import com.cloudai.core.model.Message;
import com.cloudai.core.model.TokenUsage;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Agent 执行结果。
 *
 * @param traceId          全链路追踪 ID
 * @param content          最终文本回答（COMPLETED 时为 LLM 最后一轮输出）
 * @param history          完整对话历史（可用于 resume）
 * @param turnsExecuted    实际执行的 LLM 调用轮次
 * @param toolCallsExecuted 实际执行的工具调用总数
 * @param finishStatus     终止状态
 * @param error            错误信息（ERROR 时非空）
 * @param totalUsage       累计 Token 统计
 * @author cloud-ai
 * @since 1.0
 */
public record AgentResponse(
        @Nullable String traceId,
        String content,
        List<Message> history,
        int turnsExecuted,
        int toolCallsExecuted,
        FinishStatus finishStatus,
        @Nullable String error,
        @Nullable TokenUsage totalUsage) {

    public AgentResponse {
        content = content != null ? content : "";
        history = history != null ? List.copyOf(history) : List.of();
        error = error != null ? error : "";
    }

    // ==================== 内部工厂 ====================

    private static AgentResponse of(String traceId, FinishStatus status,
                                    String content, AgentSession session) {
        return new AgentResponse(traceId, content, session.history(),
                session.turnsExecuted(), session.toolCallsExecuted(),
                status, null, session.totalUsage());
    }

    private static AgentResponse ofError(String traceId, String error, AgentSession session) {
        return new AgentResponse(traceId, "", session.history(),
                session.turnsExecuted(), session.toolCallsExecuted(),
                FinishStatus.ERROR, error, session.totalUsage());
    }

    // ==================== 公开工厂 ====================

    /** Agent 正常完成。 */
    public static AgentResponse completed(String traceId, String content, AgentSession session) {
        return of(traceId, FinishStatus.COMPLETED, content, session);
    }

    /** 达到最大轮次限制。 */
    public static AgentResponse maxTurnsReached(String traceId, AgentSession session) {
        return of(traceId, FinishStatus.MAX_TURNS_REACHED, "", session);
    }

    /** 运行超时。 */
    public static AgentResponse timeout(String traceId, AgentSession session) {
        return of(traceId, FinishStatus.TIMEOUT, "", session);
    }

    /** 被外部中断。 */
    public static AgentResponse interrupted(String traceId, AgentSession session) {
        return of(traceId, FinishStatus.INTERRUPTED, "", session);
    }

    /** 满足自定义停止条件。 */
    public static AgentResponse stopCondition(String traceId, String content, AgentSession session) {
        return of(traceId, FinishStatus.STOP_CONDITION, content, session);
    }

    /** 执行出错。 */
    public static AgentResponse error(String traceId, String error, AgentSession session) {
        return ofError(traceId, error, session);
    }

    /** 终止状态。 */
    public enum FinishStatus {
        /** LLM 返回最终回答，无需更多工具调用 */
        COMPLETED,
        /** 达到最大循环轮次 */
        MAX_TURNS_REACHED,
        /** 运行超时 */
        TIMEOUT,
        /** 被外部中断 */
        INTERRUPTED,
        /** 满足自定义停止条件 */
        STOP_CONDITION,
        /** 执行过程中出错 */
        ERROR
    }
}