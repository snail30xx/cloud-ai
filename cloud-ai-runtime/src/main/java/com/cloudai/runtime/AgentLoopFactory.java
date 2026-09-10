package com.cloudai.runtime;

import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.loop.PlanThenExecuteAgentLoop;
import com.cloudai.runtime.loop.ReActAgentLoop;
import com.cloudai.runtime.AgentType;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.runtime.StopCondition;

import java.time.Duration;
import java.util.List;

/**
 * Agent 循环工厂 — 所有 AgentLoop 实例的唯一创建入口。
 *
 * <pre>{@code
 * // 快捷创建 ReAct
 * AgentLoop loop = AgentLoopFactory.reAct(router, registry, execService,
 *         50, Duration.ofMinutes(10), List.of());
 *
 * // 快捷创建 PlanThenExecute
 * AgentLoop loop = AgentLoopFactory.planThenExecute(router, registry, execService,
 *         50, Duration.ofMinutes(10), List.of(), 10);
 *
 * // Builder 模式
 * AgentLoop loop = AgentLoopFactory.builder(router, registry, execService)
 *         .type(AgentType.PLAN_THEN_EXECUTE)
 *         .maxTurns(100)
 *         .maxPlanSteps(5)
 *         .build();
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class AgentLoopFactory {

    private AgentLoopFactory() {
    }

    /**
     * 快捷创建 ReAct Agent 循环。
     *
     * @param modelRouter          模型路由器
     * @param toolRegistry         工具注册表
     * @param toolExecutionService 工具执行服务
     * @param maxTurns             最大 LLM 调用轮次
     * @param timeout              运行超时
     * @param stopConditions       自定义停止条件（可为空）
     * @return ReAct AgentLoop 实例
     */
    public static AgentLoop reAct(ModelRouter modelRouter,
                                  ToolRegistry toolRegistry,
                                  ToolExecutionService toolExecutionService,
                                  int maxTurns,
                                  Duration timeout,
                                  List<StopCondition> stopConditions) {
        return new ReActAgentLoop(modelRouter, toolRegistry, toolExecutionService,
                maxTurns, timeout, stopConditions);
    }

    /**
     * 快捷创建 PlanThenExecute Agent 循环（默认 maxPlanSteps=10）。
     */
    public static AgentLoop planThenExecute(ModelRouter modelRouter,
                                            ToolRegistry toolRegistry,
                                            ToolExecutionService toolExecutionService,
                                            int maxTurns,
                                            Duration timeout,
                                            List<StopCondition> stopConditions) {
        return new PlanThenExecuteAgentLoop(modelRouter, toolRegistry, toolExecutionService,
                maxTurns, timeout, stopConditions);
    }

    /**
     * 快捷创建 PlanThenExecute Agent 循环（指定 maxPlanSteps）。
     */
    public static AgentLoop planThenExecute(ModelRouter modelRouter,
                                            ToolRegistry toolRegistry,
                                            ToolExecutionService toolExecutionService,
                                            int maxTurns,
                                            Duration timeout,
                                            List<StopCondition> stopConditions,
                                            int maxPlanSteps) {
        return new PlanThenExecuteAgentLoop(modelRouter, toolRegistry, toolExecutionService,
                maxTurns, timeout, stopConditions, maxPlanSteps);
    }

    /**
     * 进入 Builder 模式。
     */
    public static AgentLoopBuilder builder(ModelRouter modelRouter,
                                           ToolRegistry toolRegistry,
                                           ToolExecutionService toolExecutionService) {
        return new AgentLoopBuilder(modelRouter, toolRegistry, toolExecutionService);
    }
}