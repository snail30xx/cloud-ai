package com.cloudai.runtime.config;

import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.AgentLoopFactory;
import com.cloudai.runtime.spi.AgentLoop;
import com.cloudai.runtime.spi.StopCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 运行时模块工厂 — 替代 Spring 自动装配。
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class RuntimeAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RuntimeAutoConfiguration.class);

    private RuntimeAutoConfiguration() {}

    /**
     * 通过 AgentLoopFactory 创建 Agent 循环。
     *
     * @param modelRouter       模型路由器
     * @param toolRegistry      工具注册表
     * @param toolExecutionService 工具执行服务
     * @param properties        运行时配置
     * @param stopConditions    停止条件列表（可为空）
     */
    public static AgentLoop agentLoop(ModelRouter modelRouter,
                                       ToolRegistry toolRegistry,
                                       ToolExecutionService toolExecutionService,
                                       RuntimeProperties properties,
                                       List<StopCondition> stopConditions) {
        log.info("Creating AgentLoop via factory: type={}, maxTurns={}, timeout={}, maxPlanSteps={}",
                properties.type(), properties.maxTurns(), properties.timeout(), properties.maxPlanSteps());
        return AgentLoopFactory.builder(modelRouter, toolRegistry, toolExecutionService)
                .type(properties.type())
                .maxTurns(properties.maxTurns())
                .timeout(properties.timeout())
                .stopConditions(stopConditions != null ? stopConditions : List.of())
                .maxPlanSteps(properties.maxPlanSteps())
                .build();
    }
}