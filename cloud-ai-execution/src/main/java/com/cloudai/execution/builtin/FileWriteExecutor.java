package com.cloudai.execution.builtin;

import com.cloudai.core.tool.ToolCall;
import com.cloudai.execution.ToolResult;
import com.cloudai.execution.ToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * 文件写入执行器 — 将内容写入指定路径。
 *
 * <p>参数 JSON：{@code {"path": "/data/output.txt", "content": "hello"}}</p>
 *
 * <p>若构造时指定了 {@code baseDir}，相对路径将相对于该目录解析，
 * 绝对路径仍按原样使用。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class FileWriteExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(FileWriteExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Nullable
    private final Path baseDir;

    /** 创建无工作目录限制的执行器（路径原样使用）。 */
    public FileWriteExecutor() {
        this(null);
    }

    /**
     * 创建带工作目录的执行器。
     *
     * @param baseDir 工作目录，null 表示不限制
     */
    public FileWriteExecutor(@Nullable Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public ToolResult execute(ToolCall toolCall) {
        try {
            var parsed = extractArgs(toolCall);
            if (parsed.path() == null) {
                return ToolResult.failure(toolCall.id(), "Missing 'path' parameter");
            }
            if (parsed.content() == null) {
                return ToolResult.failure(toolCall.id(), "Missing 'content' parameter");
            }
            var filePath = resolvePath(parsed.path());
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, parsed.content());
            log.debug("File written: {} ({} chars)", parsed.path(), parsed.content().length());
            return ToolResult.success(toolCall.id(), "Written to: " + parsed.path());
        } catch (Exception e) {
            log.warn("File write failed: {}", e.getMessage());
            return ToolResult.failure(toolCall.id(), "Write failed: " + e.getMessage());
        }
    }

    private Path resolvePath(String path) {
        var p = Path.of(path);
        if (baseDir != null && !p.isAbsolute()) {
            return baseDir.resolve(p).normalize();
        }
        return p;
    }

    private ParsedArgs extractArgs(ToolCall toolCall) throws Exception {
        if (toolCall.arguments() == null || toolCall.arguments().isBlank()) {
            return new ParsedArgs(null, null);
        }
        JsonNode node = mapper.readTree(toolCall.arguments());
        var pathNode = node.get("path");
        var contentNode = node.get("content");
        return new ParsedArgs(
                pathNode != null && pathNode.isTextual() ? pathNode.asText() : null,
                contentNode != null && contentNode.isTextual() ? contentNode.asText() : null);
    }

    private record ParsedArgs(String path, String content) {}
}
