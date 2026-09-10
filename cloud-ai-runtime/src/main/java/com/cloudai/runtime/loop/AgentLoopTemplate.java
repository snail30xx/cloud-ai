package com.cloudai.runtime.loop;

import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.Message;
import com.cloudai.core.tool.ToolCall;
import com.cloudai.runtime.AgentRequest;
import com.cloudai.runtime.AgentResponse;
import com.cloudai.runtime.AgentSession;
import com.cloudai.runtime.TurnResult;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.runtime.lifecycle.SessionLifecycle;
import com.cloudai.runtime.lifecycle.StepLifecycle;
import com.cloudai.runtime.lifecycle.TurnLifecycle;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;

/**
 * Agent 循环模板 — 三级抽象（Session / Turn / Step）的编排骨架。
 *
 * <p>通过三个 SPI 接口实现策略注入，模板方法不可覆盖：
 * <ul>
 *   <li>{@link SessionLifecycle} — 会话创建、续行检查、响应构建</li>
 *   <li>{@link TurnLifecycle} — LLM 调用、工具解析、停止条件检查</li>
 *   <li>{@link StepLifecycle} — 工具执行、结果格式化</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public abstract class AgentLoopTemplate implements AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopTemplate.class);
    private static final String MDC_TRACE_ID = "traceId";

    private static final ObjectMapper JSON = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.INDENT_OUTPUT);

    // ==================== 策略注入 ====================

    protected abstract SessionLifecycle sessionLifecycle();

    protected abstract TurnLifecycle turnLifecycle();

    protected abstract StepLifecycle stepLifecycle();

    // ==================== 模板方法 ====================

    @Override
    public final AgentResponse run(AgentRequest request) {
        var sl = sessionLifecycle();
        var session = sl.createSession(request);
        var maxTurns = sl.resolveMaxTurns(request);
        var timeout = sl.resolveTimeout(request);

        sl.registerSession(session);
        MDC.put(MDC_TRACE_ID, session.traceId());
        sl.onSessionStart(session, request);

        try {
            log.info("Agent loop started: traceId={}, maxTurns={}, timeout={}",
                    session.traceId(), maxTurns, timeout);

            while (true) {
                var stopStatus = sl.checkContinuation(session, maxTurns, timeout);
                if (stopStatus != null) {
                    log.info("Agent loop stopped: traceId={}, status={}", session.traceId(), stopStatus);
                    return sl.buildStopResponse(session, stopStatus);
                }

                var turnResult = executeTurn(session, request);
                if (turnResult.terminal()) {
                    return sl.buildTerminalResponse(session, turnResult);
                }
            }
        } catch (Exception e) {
            log.error("Agent loop error: traceId={}, turns={}", session.traceId(), session.turnsExecuted(), e);
            return sl.handleError(session, e);
        } finally {
            sl.onSessionEnd(session);
            sl.unregisterSession(session);
            MDC.remove(MDC_TRACE_ID);
        }
    }

    @Override
    public void interrupt(String traceId) {
        sessionLifecycle().interrupt(traceId);
    }

    @Override
    public boolean isRunning(String traceId) {
        return sessionLifecycle().isRunning(traceId);
    }

    // ==================== Turn 级模板 ====================

    protected TurnResult executeTurn(AgentSession session, AgentRequest request) {
        var tl = turnLifecycle();
        var sl = stepLifecycle();

        tl.beforeTurn(session);

        var response = tl.callModel(session, request);
        session.incrementTurn();
        session.accumulateUsage(response.usage());
        session.addMessage(Message.assistant(response.content(), response.toolCalls()));

        logJson("[LLM Response]", session.traceId(), session.turnsExecuted(), response);

        tl.afterModelCall(session, response);

        var stopStatus = tl.checkStopConditions(session, response);
        if (stopStatus != null) {
            log.info("Stop condition met: traceId={}, status={}", session.traceId(), stopStatus);
            return TurnResult.terminal(response, stopStatus, response.content());
        }

        if (response.toolCalls().isEmpty()) {
            log.info("Agent loop completed: traceId={}, turns={}, toolCalls={}",
                    session.traceId(), session.turnsExecuted(), session.toolCallsExecuted());
            return TurnResult.terminal(response, AgentResponse.FinishStatus.COMPLETED, response.content());
        }

        sl.executeSteps(session, response.toolCalls());

        tl.afterTurn(session, response);

        return TurnResult.continuing(response);
    }

    // ==================== 内部方法 ====================

    private void logJson(String tag, String traceId, int turn, Object payload) {
        if (!log.isDebugEnabled()) {
            return;
        }
        try {
            var json = JSON.writeValueAsString(payload);
            log.debug("{} traceId={}, turn={}\n{}", tag, traceId, turn, json);
        } catch (Exception e) {
            log.warn("{} traceId={}, turn={}, serialization error: {}", tag, traceId, turn, e.getMessage());
        }
    }
}
