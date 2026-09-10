package com.cloudai.runtime.lifecycle;

import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.runtime.AgentRequest;
import com.cloudai.runtime.AgentResponse;
import com.cloudai.runtime.AgentSession;

import java.util.List;

/**
 * Turn 级生命周期 — 定义单轮迭代的 LLM 调用和停止条件检查。
 *
 * <p>对应模板方法中的 Turn 层（由 {@code AgentLoopTemplate.executeTurn} 编排）：
 * <pre>{@code
 * beforeTurn(session)
 * callModel(session, request)          // abstract
 * afterModelCall(session, response)
 * checkStopConditions(session, response)  // 默认 null
 * if (toolCalls.isEmpty()) COMPLETED
 * stepLifecycle.executeSteps(session, toolCalls)
 * afterTurn(session, response)
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface TurnLifecycle {

    /**
     * 调用 LLM（必须实现）。
     *
     * @param session 当前会话
     * @param request 调用请求
     * @return LLM 响应
     */
    ChatResponse callModel(AgentSession session, AgentRequest request);

    /**
     * 解析可用工具定义列表（用于构建 ChatRequest 和日志）。
     *
     * @param request 调用请求
     * @return 工具定义列表，空列表表示无可用工具
     */
    default List<ToolDefinition> resolveTools(AgentRequest request) {
        return List.of();
    }

    /** Turn 前置钩子（默认空实现）。 */
    default void beforeTurn(AgentSession session) {
        // 默认空实现
    }

    /** LLM 调用后钩子（默认空实现）。 */
    default void afterModelCall(AgentSession session, ChatResponse response) {
        // 默认空实现
    }

    /**
     * 检查自定义停止条件。
     *
     * @return 非 null 表示满足停止条件，null 表示继续
     */
    default AgentResponse.FinishStatus checkStopConditions(AgentSession session, ChatResponse response) {
        return null;
    }

    /** Turn 后置钩子（默认空实现）。 */
    default void afterTurn(AgentSession session, ChatResponse response) {
        // 默认空实现
    }
}
