package com.cloudai.spring.config;

import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.security.SecurityInterceptor;
import com.cloudai.spring.properties.CloudAiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.execution.enabled", havingValue = "true", matchIfMissing = true)
public class ExecutionAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(CloudAiProperties props) {
        var exec = props.execution() != null ? props.execution() : new CloudAiProperties.Execution();
        var executionProps = new com.cloudai.execution.config.ExecutionProperties(
                exec.fileReadEnabled(),
                exec.fileWriteEnabled(),
                exec.shellEnabled(),
                exec.shellTimeout(),
                exec.workspace());
        return com.cloudai.execution.config.ExecutionAutoConfiguration.toolRegistry(executionProps);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutionService toolExecutionService(
            ToolRegistry toolRegistry,
            SecurityInterceptor securityInterceptor) {
        return com.cloudai.execution.config.ExecutionAutoConfiguration.toolExecutionService(
                toolRegistry, securityInterceptor);
    }
}