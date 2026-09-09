package com.cloudai.execution.config;

import com.cloudai.core.model.ToolDefinition;
import com.cloudai.execution.impl.DefaultToolRegistry;
import com.cloudai.execution.impl.FileReadExecutor;
import com.cloudai.execution.impl.FileWriteExecutor;
import com.cloudai.execution.impl.ShellToolExecutor;
import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.security.impl.SecurityInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

/**
 * 执行模块自动装配。
 *
 * <p>通过 {@code cloud-ai.execution.enabled} 控制（默认 true）。
 * 自动注册内置工具（file_read、file_write、shell_exec）和 {@link ToolExecutionService}。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(ExecutionProperties.class)
@ConditionalOnProperty(name = "cloud-ai.execution.enabled", havingValue = "true", matchIfMissing = true)
public class ExecutionAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ExecutionAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry(Environment env) {
        var registry = new DefaultToolRegistry();

        var fileReadEnabled = env.getProperty("cloud-ai.execution.file-read-enabled", Boolean.class, true);
        var fileWriteEnabled = env.getProperty("cloud-ai.execution.file-write-enabled", Boolean.class, true);
        var shellEnabled = env.getProperty("cloud-ai.execution.shell-enabled", Boolean.class, true);

        if (fileReadEnabled) {
            registry.register(
                    new ToolDefinition("file_read", "Read file content",
                            Map.of("type", "object", "properties",
                                    Map.of("path", Map.of("type", "string", "description", "File path")),
                                    "required", List.of("path"))),
                    new FileReadExecutor());
            log.info("Registered tool: file_read");
        }

        if (fileWriteEnabled) {
            registry.register(
                    new ToolDefinition("file_write", "Write content to file",
                            Map.of("type", "object", "properties",
                                    Map.of(
                                            "path", Map.of("type", "string", "description", "File path"),
                                            "content", Map.of("type", "string", "description", "Content to write")),
                                    "required", List.of("path", "content"))),
                    new FileWriteExecutor());
            log.info("Registered tool: file_write");
        }

        if (shellEnabled) {
            registry.register(
                    new ToolDefinition("shell_exec", "Execute a shell command",
                            Map.of("type", "object", "properties",
                                    Map.of("command", Map.of("type", "string", "description", "Shell command")),
                                    "required", List.of("command"))),
                    new ShellToolExecutor());
            log.info("Registered tool: shell_exec");
        }

        log.info("ToolRegistry initialized with {} tool(s)", registry.size());
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolExecutionService toolExecutionService(
            ToolRegistry toolRegistry,
            SecurityInterceptor securityInterceptor) {
        log.info("Creating ToolExecutionService");
        return new ToolExecutionService(toolRegistry, securityInterceptor);
    }
}
