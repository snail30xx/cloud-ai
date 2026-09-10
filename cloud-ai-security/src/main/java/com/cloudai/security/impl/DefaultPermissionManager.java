package com.cloudai.security.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.security.model.OperationType;
import com.cloudai.security.model.PermissionResult;
import com.cloudai.security.model.SecurityContext;
import com.cloudai.security.spi.PermissionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cloudai.security.util.AntPathMatcher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认权限管理器 — 基于白名单/黑名单规则的权限校验。
 *
 * <p>规则匹配优先级：黑名单 > 白名单 > 默认拒绝。</p>
 * <p>支持 Ant 风格路径匹配（如 {@code /workspace/**}）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultPermissionManager implements PermissionManager {
    private static final Logger log = LoggerFactory.getLogger(DefaultPermissionManager.class);
        // AntPathMatcher 改为静态方法调用
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final List<PermissionRule> rules = new CopyOnWriteArrayList<>();

    /** 添加允许规则。 */
    public DefaultPermissionManager allow(OperationType operation, String targetPattern) {
        rules.add(new PermissionRule(operation, targetPattern, true));
        log.info("Permission allow rule added: {} {}", operation, targetPattern);
        return this;
    }

    /** 添加拒绝规则。 */
    public DefaultPermissionManager deny(OperationType operation, String targetPattern) {
        rules.add(new PermissionRule(operation, targetPattern, false));
        log.info("Permission deny rule added: {} {}", operation, targetPattern);
        return this;
    }

    @Override
    public PermissionResult check(ToolCall toolCall, SecurityContext context) {
        if (rules.isEmpty()) {
            log.warn("No permission rules configured, deny by default: tool={}, agent={}",
                    toolCall.name(), context.agentId());
            return PermissionResult.deny("No permission rules configured, deny by default");
        }

        var opType = OperationType.from(toolCall.name());
        var target = extractTarget(toolCall);

        // 黑名单优先匹配
        for (var rule : rules) {
            if (!rule.allowed && rule.matches(opType, target)) {
                log.info("Permission DENIED by blacklist: tool={}, target={}, agent={}",
                        toolCall.name(), target, context.agentId());
                return PermissionResult.deny("Denied by rule: " + rule);
            }
        }

        // 白名单匹配
        for (var rule : rules) {
            if (rule.allowed && rule.matches(opType, target)) {
                log.debug("Permission ALLOWED by whitelist: tool={}, target={}, agent={}",
                        toolCall.name(), target, context.agentId());
                return PermissionResult.allow("Allowed by rule: " + rule);
            }
        }

        log.warn("No matching rule for tool={}, target={}, agent={}, deny by default",
                toolCall.name(), target, context.agentId());
        return PermissionResult.deny("No matching rule for operation: " + toolCall.name());
    }

    /**
     * 从工具参数中提取目标路径。
     *
     * <p>支持 JSON 格式参数（提取 path/file/filePath/target/command/url 字段）
     * 和纯文本参数（直接作为目标）。</p>
     *
     * @param toolCall 工具调用
     * @return 目标路径，无匹配时返回空字符串
     */
    public static String extractTarget(ToolCall toolCall) {
        if (toolCall.arguments() == null || toolCall.arguments().isBlank()) {
            return "";
        }
        var args = toolCall.arguments().trim();

        // If it's not JSON, treat the argument itself as the target
        if (!args.startsWith("{")) {
            return args;
        }

        // Use Jackson to parse JSON and extract common path fields
        try {
            JsonNode node = objectMapper.readTree(args);
            for (var key : new String[]{"path", "file", "filePath", "target", "command", "url"}) {
                JsonNode valueNode = node.get(key);
                if (valueNode != null && valueNode.isTextual()) {
                    return valueNode.asText();
                }
            }
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse tool arguments as JSON, falling back to empty target: {}", args, e);
        }
        return "";
    }

    /** 权限规则：操作类型 + 目标模式 + 允许/拒绝。 */
    private record PermissionRule(OperationType operation, String targetPattern, boolean allowed) {
        boolean matches(OperationType opType, String target) {
            if (opType != operation) return false;
            if (targetPattern.equals("*") || targetPattern.equals("**")) return true;
            if (target.isEmpty()) return false;
            return AntPathMatcher.match(targetPattern, target);
        }

        @Override
        public String toString() {
            return (allowed ? "ALLOW" : "DENY") + " " + operation + " " + targetPattern;
        }
    }
}
