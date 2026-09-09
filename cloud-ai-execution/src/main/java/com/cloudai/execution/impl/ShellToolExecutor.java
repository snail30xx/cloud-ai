package com.cloudai.execution.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.execution.model.ToolResult;
import com.cloudai.execution.spi.ToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Shell 命令执行器 — 在系统 shell 中执行命令。
 *
 * <p>参数 JSON：{@code {"command": "ls -la /tmp"}}</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ShellToolExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(ShellToolExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    @Override
    public ToolResult execute(ToolCall toolCall) {
        try {
            var command = extractCommand(toolCall);
            if (command == null || command.isBlank()) {
                return ToolResult.failure(toolCall.id(), "Missing 'command' parameter");
            }

            log.info("Executing shell command: {}", command);
            var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            var processBuilder = new ProcessBuilder(
                    isWindows ? new String[]{"cmd", "/c", command} : new String[]{"sh", "-c", command});
            processBuilder.redirectErrorStream(true);
            var process = processBuilder.start();

            var output = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            var finished = process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure(toolCall.id(), "Command timed out after 30s");
            }

            var exitCode = process.exitValue();
            var result = output.toString().trim();
            if (exitCode != 0) {
                log.warn("Shell command failed: exit={}, command={}", exitCode, command);
                return ToolResult.failure(toolCall.id(),
                        "Exit code " + exitCode + ": " + result);
            }
            log.debug("Shell command succeeded: {}", command);
            return ToolResult.success(toolCall.id(), result);
        } catch (Exception e) {
            log.warn("Shell execution failed: {}", e.getMessage());
            return ToolResult.failure(toolCall.id(), "Execution failed: " + e.getMessage());
        }
    }

    private String extractCommand(ToolCall toolCall) throws Exception {
        if (toolCall.arguments() == null || toolCall.arguments().isBlank()) {
            return null;
        }
        JsonNode node = mapper.readTree(toolCall.arguments());
        var cmdNode = node.get("command");
        return cmdNode != null && cmdNode.isTextual() ? cmdNode.asText() : null;
    }
}
