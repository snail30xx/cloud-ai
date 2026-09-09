package com.cloudai.runtime;

import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.impl.PlanThenExecuteAgentLoop;
import com.cloudai.runtime.impl.ReActAgentLoop;
import com.cloudai.runtime.model.AgentType;
import com.cloudai.runtime.spi.AgentLoop;
import com.cloudai.runtime.spi.StopCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Agent 循环 Builder — 流式配置 AgentLoop 各参数。
 *
 * <pre>{@code
 * AgentLoop loop = AgentLoopFactory.builder(router, registry, execService)
 *         .type(AgentType.PLAN_THEN_EXECUTE)
 *         .maxTurns(100)
 *         .timeout(Duration.ofMinutes(30))
 *         .maxPlanSteps(5)
 *         .stopConditions(List.of(tokenBudget))
 *         .build();
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class AgentLoopBuilder {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopBuilder.class);

    private final ModelRouter modelRouter;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;

    private AgentType type = AgentType.REACT;
    private int maxTurns = 50;
    private Duration timeout = Duration.ofMinutes(10);
    private List<StopCondition> stopConditions = List.of();
    private int maxPlanSteps = 10;

    AgentLoopBuilder(ModelRouter modelRouter,
                     ToolRegistry toolRegistry,
                     ToolExecutionService toolExecutionService) {
        if (modelRouter == null) {
            throw new IllegalArgumentException("modelRouter must not be null");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry must not be null");
        }
        if (toolExecutionService == null) {
            throw new IllegalArgumentException("toolExecutionService must not be null");
        }
        this.modelRouter = modelRouter;
        this.toolRegistry = toolRegistry;
        this.toolExecutionService = toolExecutionService;
    }

    /** 设置 Agent 类型（默认 REACT）。 */
    public AgentLoopBuilder type(AgentType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
        return this;
    }

    /** 设置最大 LLM 调用轮次（默认 50）。 */
    public AgentLoopBuilder maxTurns(int maxTurns) {
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("maxTurns must be positive");
        }
        this.maxTurns = maxTurns;
        return this;
    }

    /** 设置运行超时（默认 10 分钟）。 */
    public AgentLoopBuilder timeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        this.timeout = timeout;
        return this;
    }

    /** 设置自定义停止条件列表（默认空）。 */
    public AgentLoopBuilder stopConditions(List<StopCondition> stopConditions) {
        this.stopConditions = stopConditions != null ? List.copyOf(stopConditions) : List.of();
        return this;
    }

    /** 设置规划阶段最大步数（仅 PLAN_THEN_EXECUTE 生效，默认 10）。 */
    public AgentLoopBuilder maxPlanSteps(int maxPlanSteps) {
        if (maxPlanSteps <= 0) {
            throw new IllegalArgumentException("maxPlanSteps must be positive");
        }
        this.maxPlanSteps = maxPlanSteps;
        return this;
    }

    /** 构建 AgentLoop 实例。 */
    public AgentLoop build() {
        log.info("Creating AgentLoop: type={}, maxTurns={}, timeout={}, stopConditions={}, maxPlanSteps={}",
                type, maxTurns, timeout, stopConditions.size(), maxPlanSteps);
        return switch (type) {
            case REACT -> new ReActAgentLoop(modelRouter, toolRegistry, toolExecutionService,
                    maxTurns, timeout, stopConditions);
            case PLAN_THEN_EXECUTE -> new PlanThenExecuteAgentLoop(modelRouter, toolRegistry,
                    toolExecutionService, maxTurns, timeout, stopConditions, maxPlanSteps);
        };
    }
}