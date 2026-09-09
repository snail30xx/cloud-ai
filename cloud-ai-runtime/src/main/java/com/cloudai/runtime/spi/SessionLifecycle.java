package com.cloudai.runtime.spi;

import com.cloudai.runtime.model.AgentRequest;
import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.runtime.model.AgentSession;
import com.cloudai.runtime.model.TurnResult;

import java.time.Duration;

/**
 * Session 级生命周期 — 定义 Agent 会话的创建、检查和终止行为。
 *
 * <p>对应模板方法中的 Session 层：
 * <pre>{@code
 * createSession(request)
 * registerSession(session)
 * onSessionStart(session, request)
 * while (checkContinuation() == null) executeTurn()
 * onSessionEnd(session)
 * unregisterSession(session)
 * }</pre>
 *
 * <p>默认实现提供了中断/超时/maxTurns 检查和响应构建逻辑，
 * 子类通常只需覆盖 {@link #createSession}、{@link #resolveMaxTurns}、
 * {@link #resolveTimeout}、{@link #onSessionStart} 和
 * {@link #registerSession}/{@link #unregisterSession}。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface SessionLifecycle {

    /**
     * 创建会话。
     *
     * @param request 调用请求
     * @return 新的 AgentSession
     */
    AgentSession createSession(AgentRequest request);

    /**
     * 解析最大 LLM 调用轮次。
     */
    int resolveMaxTurns(AgentRequest request);

    /**
     * 解析运行超时。
     */
    Duration resolveTimeout(AgentRequest request);

    /** 注册会话，用于 interrupt 追踪（默认空实现）。 */
    default void registerSession(AgentSession session) {
        // 默认空实现
    }

    /** 注销会话（默认空实现）。 */
    default void unregisterSession(AgentSession session) {
        // 默认空实现
    }

    /** 会话启动回调（默认空实现）。 */
    default void onSessionStart(AgentSession session, AgentRequest request) {
        // 默认空实现
    }

    /** 会话结束回调（默认空实现）。 */
    default void onSessionEnd(AgentSession session) {
        // 默认空实现
    }

    /**
     * 检查是否应继续循环。
     *
     * @return 非 null 表示应停止并返回对应 FinishStatus，null 表示继续
     */
    default AgentResponse.FinishStatus checkContinuation(
            AgentSession session, int maxTurns, Duration timeout) {
        if (session.isInterrupted()) {
            return AgentResponse.FinishStatus.INTERRUPTED;
        }
        if (session.isTimedOut(timeout)) {
            return AgentResponse.FinishStatus.TIMEOUT;
        }
        if (session.turnsExecuted() >= maxTurns) {
            return AgentResponse.FinishStatus.MAX_TURNS_REACHED;
        }
        return null;
    }

    /** 构建停止响应（INTERRUPTED / TIMEOUT / MAX_TURNS_REACHED）。 */
    default AgentResponse buildStopResponse(AgentSession session, AgentResponse.FinishStatus status) {
        return switch (status) {
            case INTERRUPTED -> AgentResponse.interrupted(session.traceId(), session);
            case TIMEOUT -> AgentResponse.timeout(session.traceId(), session);
            case MAX_TURNS_REACHED -> AgentResponse.maxTurnsReached(session.traceId(), session);
            default -> AgentResponse.error(session.traceId(), "Unexpected stop: " + status, session);
        };
    }

    /** 构建终止响应（COMPLETED / STOP_CONDITION）。 */
    default AgentResponse buildTerminalResponse(AgentSession session, TurnResult turnResult) {
        var status = turnResult.finishStatus();
        var content = turnResult.content() != null ? turnResult.content() : "";
        return switch (status) {
            case COMPLETED -> AgentResponse.completed(session.traceId(), content, session);
            case STOP_CONDITION -> AgentResponse.stopCondition(session.traceId(), content, session);
            default -> AgentResponse.error(session.traceId(), "Unexpected terminal: " + status, session);
        };
    }

    /** 处理未捕获异常。 */
    default AgentResponse handleError(AgentSession session, Exception e) {
        return AgentResponse.error(session.traceId(), e.getMessage(), session);
    }

    /** 中断指定 traceId 的运行中会话（默认空实现）。 */
    default void interrupt(String traceId) {
        // 默认空实现
    }

    /** 检查指定 traceId 的会话是否正在运行（默认 false）。 */
    default boolean isRunning(String traceId) {
        return false;
    }
}