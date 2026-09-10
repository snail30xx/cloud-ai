package com.cloudai.spring.config;

import com.cloudai.core.chat.ContextManager;
import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.memory.config.MemoryProperties;
import com.cloudai.runtime.config.RuntimeProperties;
import com.cloudai.runtime.AgentType;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.runtime.StopCondition;
import com.cloudai.runtime.loop.ReActAgentLoop;
import com.cloudai.spring.properties.CloudAiProperties;
import org.springframework.beans.factory.ObjectProvider;
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
            List<StopCondition> stopConditions,
            ObjectProvider<ContextManager> contextManagerProvider,
            ObjectProvider<MemoryProperties> memoryPropertiesProvider) {
        var rt = props.runtime() != null ? props.runtime() : new CloudAiProperties.Runtime();
        var type = rt.type() != null
                ? AgentType.valueOf(rt.type().toUpperCase())
                : AgentType.REACT;
        var runtimeProps = new RuntimeProperties(
                rt.maxTurns(),
                rt.timeout() != null ? rt.timeout() : Duration.ofMinutes(10),
                type,
                rt.maxPlanSteps());
        // memory 模块可被禁用：ContextManager 缺失时不裁剪历史，预算回退默认值
        var contextManager = contextManagerProvider.getIfAvailable();
        var memoryProps = memoryPropertiesProvider.getIfAvailable();
        var maxContextTokens = memoryProps != null
                ? memoryProps.maxContextTokens()
                : ReActAgentLoop.DEFAULT_MAX_CONTEXT_TOKENS;
        return com.cloudai.runtime.config.RuntimeAutoConfiguration.agentLoop(
                modelRouter, toolRegistry, toolExecutionService, runtimeProps, stopConditions,
                contextManager, maxContextTokens);
    }
}