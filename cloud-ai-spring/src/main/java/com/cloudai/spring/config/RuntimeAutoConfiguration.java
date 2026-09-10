package com.cloudai.spring.config;

import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.config.RuntimeProperties;
import com.cloudai.runtime.AgentType;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.runtime.StopCondition;
import com.cloudai.spring.properties.CloudAiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class RuntimeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AgentLoop agentLoop(
            ModelRouter modelRouter,
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            CloudAiProperties props,
            List<StopCondition> stopConditions) {
        var rt = props.runtime() != null ? props.runtime() : new CloudAiProperties.Runtime();
        var type = rt.type() != null
                ? AgentType.valueOf(rt.type().toUpperCase())
                : AgentType.REACT;
        var runtimeProps = new RuntimeProperties(
                rt.maxTurns(),
                rt.timeout() != null ? rt.timeout() : Duration.ofMinutes(10),
                type,
                rt.maxPlanSteps());
        return com.cloudai.runtime.config.RuntimeAutoConfiguration.agentLoop(
                modelRouter, toolRegistry, toolExecutionService, runtimeProps, stopConditions);
    }
}