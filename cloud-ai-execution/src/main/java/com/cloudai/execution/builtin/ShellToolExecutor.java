package com.cloudai.execution.builtin;

import com.cloudai.core.tool.ToolCall;
import com.cloudai.execution.ToolResult;
import com.cloudai.execution.ToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Shell 命令执行器 — 在系统 shell 中执行命令。
 *
 * <p>参数 JSON：{@code {"command": "ls -la /tmp"}}</p>
 *
 * <p>若构造时指定了 {@code baseDir}，命令将在该目录下执行。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ShellToolExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(ShellToolExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    @Nullable
    private final Path baseDir;
    private final Duration timeout;

    /** 创建无工作目录限制、默认 30s 超时的执行器。 */
    public ShellToolExecutor() {
        this(null, null);
    }

    /**
     * 创建带工作目录、默认 30s 超时的执行器。
     *
     * @param baseDir 工作目录，null 表示不限制
     */
    public ShellToolExecutor(@Nullable Path baseDir) {
        this(baseDir, null);
    }

    /**
     * 创建带工作目录和超时的执行器。
     *
     * @param baseDir 工作目录，null 表示不限制
     * @param timeout 单条命令超时，null 或非正值使用默认 30s
     */
    public ShellToolExecutor(@Nullable Path baseDir, @Nullable Duration timeout) {
        this.baseDir = baseDir;
        this.timeout = (timeout == null || timeout.isNegative() || timeout.isZero())
                ? Duration.ofMillis(DEFAULT_TIMEOUT_MS)
                : timeout;
    }

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
            if (baseDir != null) {
                processBuilder.directory(baseDir.toFile());
            }
            var process = processBuilder.start();

            // 输出必须在后台线程读取：主线程负责超时判定，若同步读会阻塞到进程输出结束，超时失效
            var output = new StringBuilder();
            var readerThread = new Thread(() -> {
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (output) {
                            output.append(line).append("\n");
                        }
                    }
                } catch (Exception ignored) {
                    // 进程被超时终止时流关闭抛异常，属预期路径
                }
            }, "shell-tool-output-" + toolCall.id());
            readerThread.setDaemon(true);
            readerThread.start();

            var finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                readerThread.join(1000);
                return ToolResult.failure(toolCall.id(),
                        "Command timed out after " + timeout.toSeconds() + "s");
            }
            readerThread.join(2000);

            var exitCode = process.exitValue();
            String result;
            synchronized (output) {
                result = output.toString().trim();
            }
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
