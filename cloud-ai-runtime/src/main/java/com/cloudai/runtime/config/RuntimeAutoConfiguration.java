package com.cloudai.runtime.config;

import com.cloudai.core.chat.ContextManager;
import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.AgentLoopFactory;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.runtime.StopCondition;
import org.jspecify.annotations.Nullable;
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
     * 通过 AgentLoopFactory 创建 Agent 循环（不启用上下文压缩）。
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
        return agentLoop(modelRouter, toolRegistry, toolExecutionService,
                properties, stopConditions, null,
                com.cloudai.runtime.loop.ReActAgentLoop.DEFAULT_MAX_CONTEXT_TOKENS);
    }

    /**
     * 通过 AgentLoopFactory 创建 Agent 循环（可选上下文压缩）。
     *
     * @param contextManager 上下文管理器，null 表示不裁剪历史；
     *                       非 null 时每次调用 LLM 前把历史裁剪到 maxContextTokens 内
     * @param maxContextTokens 历史 token 预算，仅在 contextManager 非 null 时生效
     */
    public static AgentLoop agentLoop(ModelRouter modelRouter,
                                       ToolRegistry toolRegistry,
                                       ToolExecutionService toolExecutionService,
                                       RuntimeProperties properties,
                                       List<StopCondition> stopConditions,
                                       @Nullable ContextManager contextManager,
                                       int maxContextTokens) {
        log.info("Creating AgentLoop via factory: type={}, maxTurns={}, timeout={}, maxPlanSteps={}, "
                        + "contextManager={}, maxContextTokens={}",
                properties.type(), properties.maxTurns(), properties.timeout(), properties.maxPlanSteps(),
                contextManager != null ? contextManager.getClass().getSimpleName() : "off",
                contextManager != null ? maxContextTokens : "n/a");
        return AgentLoopFactory.builder(modelRouter, toolRegistry, toolExecutionService)
                .type(properties.type())
                .maxTurns(properties.maxTurns())
                .timeout(properties.timeout())
                .stopConditions(stopConditions != null ? stopConditions : List.of())
                .maxPlanSteps(properties.maxPlanSteps())
                .contextManager(contextManager)
                .maxContextTokens(maxContextTokens)
                .build();
    }
}