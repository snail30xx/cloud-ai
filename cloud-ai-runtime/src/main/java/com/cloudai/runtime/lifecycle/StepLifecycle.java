package com.cloudai.runtime.lifecycle;

import com.cloudai.core.chat.Message;
import com.cloudai.core.tool.ToolCall;
import com.cloudai.execution.ToolResult;
import com.cloudai.runtime.AgentSession;

import java.util.List;

/**
 * Step 级生命周期 — 定义单个工具调用的执行和结果格式化。
 *
 * <p>对应模板方法中的 Step 层（由 {@code StepLifecycle.executeSteps} 编排）：
 * <pre>{@code
 * for each toolCall:
 *     beforeStep(session, toolCall)
 *     doStep(session, toolCall)               // abstract
 *     formatStepResult(result)                // 默认实现
 *     session.addMessage(tool(id, formatted))
 *     session.incrementToolCall()
 *     afterStep(session, toolCall, result)
 * }</pre>
 *
 * <p>{@link #executeSteps} 是 Step 级模板方法（default 实现），
 * 子类可覆盖以定制批量执行、并行执行等策略。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface StepLifecycle {

    /**
     * 执行单个工具调用（必须实现）。
     *
     * @param session  当前会话
     * @param toolCall 工具调用
     * @return 执行结果
     */
    ToolResult doStep(AgentSession session, ToolCall toolCall);

    /** Step 前置钩子（默认空实现）。 */
    default void beforeStep(AgentSession session, ToolCall toolCall) {
        // 默认空实现
    }

    /** Step 后置钩子（默认空实现）。 */
    default void afterStep(AgentSession session, ToolCall toolCall, ToolResult result) {
        // 默认空实现
    }

    /**
     * 格式化工具执行结果为 LLM 可理解的消息（默认实现）。
     *
     * <p>成功时返回 output，失败时返回 "Error: ..."。</p>
     */
    default String formatStepResult(ToolResult result) {
        if (result.success()) {
            return result.output();
        }
        var error = result.error();
        return (error != null && !error.isBlank()) ? "Error: " + error : "Error: unknown";
    }

    /**
     * Step 级模板方法 — 逐个执行工具调用。
     *
     * <p>子类可覆盖以定制并行执行、批量回滚等策略。</p>
     */
    default void executeSteps(AgentSession session, List<ToolCall> toolCalls) {
        for (var toolCall : toolCalls) {
            beforeStep(session, toolCall);

            var result = doStep(session, toolCall);
            var formatted = formatStepResult(result);
            session.addMessage(Message.tool(toolCall.id(), formatted));
            session.incrementToolCall();

            afterStep(session, toolCall, result);
        }
    }
}