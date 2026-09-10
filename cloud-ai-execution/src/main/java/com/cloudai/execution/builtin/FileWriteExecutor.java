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

/**
 * 文件写入执行器 — 将内容写入指定路径。
 *
 * <p>参数 JSON：{@code {"path": "/data/output.txt", "content": "hello"}}</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class FileWriteExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(FileWriteExecutor.class);
    private static final ObjectMapper mapper = new ObjectMapper();

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
            var filePath = Path.of(parsed.path());
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
