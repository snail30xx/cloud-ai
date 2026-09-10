package com.cloudai.execution.config;

import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.execution.registry.DefaultToolRegistry;
import com.cloudai.execution.builtin.FileReadExecutor;
import com.cloudai.execution.builtin.FileWriteExecutor;
import com.cloudai.execution.builtin.ShellToolExecutor;
import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.security.SecurityInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 执行模块工厂 — 替代 Spring 自动装配。
 *
 * <p>自动注册内置工具（file_read、file_write、shell_exec）和 ToolExecutionService。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class ExecutionAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ExecutionAutoConfiguration.class);

    private ExecutionAutoConfiguration() {}

    /**
     * 创建工具注册表并注册内置工具。
     *
     * @param fileReadEnabled  是否启用 file_read 工具
     * @param fileWriteEnabled 是否启用 file_write 工具
     * @param shellEnabled     是否启用 shell_exec 工具
     */
    public static ToolRegistry toolRegistry(boolean fileReadEnabled, boolean fileWriteEnabled, boolean shellEnabled) {
        var registry = new DefaultToolRegistry();

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

    public static ToolExecutionService toolExecutionService(
            ToolRegistry toolRegistry,
            SecurityInterceptor securityInterceptor) {
        log.info("Creating ToolExecutionService");
        return new ToolExecutionService(toolRegistry, securityInterceptor);
    }
}