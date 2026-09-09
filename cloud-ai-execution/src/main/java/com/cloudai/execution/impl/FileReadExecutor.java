package com.cloudai.execution.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.execution.model.ToolResult;
import com.cloudai.execution.spi.ToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * 文件读取执行器 — 读取指定路径的文件内容。
 *
 * <p>参数 JSON：{@code {"path": "/data/file.txt"}}</p>
 *
 * <p>若构造时指定了 {@code baseDir}，相对路径将相对于该目录解析，
 * 绝对路径仍按原样使用。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class FileReadExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(FileReadExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Nullable
    private final Path baseDir;

    /** 创建无工作目录限制的执行器（路径原样使用）。 */
    public FileReadExecutor() {
        this(null);
    }

    /**
     * 创建带工作目录的执行器。
     *
     * @param baseDir 工作目录，null 表示不限制
     */
    public FileReadExecutor(@Nullable Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public ToolResult execute(ToolCall toolCall) {
        try {
            var path = extractPath(toolCall);
            if (path == null) {
                return ToolResult.failure(toolCall.id(), "Missing 'path' parameter");
            }
            var filePath = resolvePath(path);
            if (!Files.exists(filePath)) {
                return ToolResult.failure(toolCall.id(), "File not found: " + path);
            }
            if (!Files.isRegularFile(filePath)) {
                return ToolResult.failure(toolCall.id(), "Not a regular file: " + path);
            }
            var content = Files.readString(filePath);
            log.debug("File read: {} ({} chars)", path, content.length());
            return ToolResult.success(toolCall.id(), content);
        } catch (Exception e) {
            log.warn("File read failed: {}", e.getMessage());
            return ToolResult.failure(toolCall.id(), "Read failed: " + e.getMessage());
        }
    }

    private Path resolvePath(String path) {
        var p = Path.of(path);
        if (baseDir != null && !p.isAbsolute()) {
            return baseDir.resolve(p).normalize();
        }
        return p;
    }

    private String extractPath(ToolCall toolCall) throws Exception {
        if (toolCall.arguments() == null || toolCall.arguments().isBlank()) {
            return null;
        }
        JsonNode node = mapper.readTree(toolCall.arguments());
        var pathNode = node.get("path");
        return pathNode != null && pathNode.isTextual() ? pathNode.asText() : null;
    }
}
