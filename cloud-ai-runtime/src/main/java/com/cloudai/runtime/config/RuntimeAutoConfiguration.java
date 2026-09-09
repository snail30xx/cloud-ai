package com.cloudai.runtime.config;

import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.AgentLoopFactory;
import com.cloudai.runtime.spi.AgentLoop;
import com.cloudai.runtime.spi.StopCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 运行时模块自动装配。
 *
 * <p>通过 {@code cloud-ai.runtime.enabled} 控制（默认 true）。
 * 通过 {@code cloud-ai.agent.type} 选择 Agent 类型（默认 react）。
 * 装配 {@link AgentLoop} Bean，允许外部替换。
 * 自动注入所有 {@link StopCondition} Bean（可为空）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(RuntimeProperties.class)
@ConditionalOnProperty(name = "cloud-ai.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class RuntimeAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RuntimeAutoConfiguration.class);

    /**
     * 通过 {@link AgentLoopFactory} 创建 Agent 循环。
     */
    @Bean
    @ConditionalOnMissingBean
    public AgentLoop agentLoop(ModelRouter modelRouter,
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
                .stopConditions(stopConditions)
                .maxPlanSteps(properties.maxPlanSteps())
                .build();
    }
}