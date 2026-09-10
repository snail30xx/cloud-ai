package com.cloudai.execution;

import com.cloudai.core.tool.ToolCall;
import com.cloudai.execution.ToolResult;
import com.cloudai.execution.ToolExecutor;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.security.SecurityInterceptor;
import com.cloudai.security.audit.AuditEvent;
import com.cloudai.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具执行服务 — 编排 {@link ToolRegistry} 和 {@link SecurityInterceptor}。
 *
 * <p>单一入口：查找工具 → 安全拦截 → 执行 → 审计记录。
 * 调用方只需调用 {@link #execute(ToolCall, SecurityContext)}，无需手动串联安全和执行。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ToolExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ToolExecutionService.class);

    private final ToolRegistry registry;
    private final SecurityInterceptor interceptor;

    public ToolExecutionService(ToolRegistry registry, SecurityInterceptor interceptor) {
        this.registry = registry;
        this.interceptor = interceptor;
    }

    /**
     * 执行工具调用（含安全拦截和审计）。
     *
     * @param toolCall 工具调用
     * @param context  安全上下文
     * @return 执行结果
     */
    public ToolResult execute(ToolCall toolCall, SecurityContext context) {
        // 1. 查找执行器
        var executor = registry.find(toolCall.name());
        if (executor == null) {
            log.warn("Tool not found: {}", toolCall.name());
            interceptor.afterExecution(toolCall, context, AuditEvent.RESULT_ERROR + ": tool not found");
            return ToolResult.failure(toolCall.id(), "Tool not found: " + toolCall.name());
        }

        // 2. 安全拦截（权限 + 审批 + 审计决策）
        var permResult = interceptor.beforeExecution(toolCall, context);
        if (!permResult.allowed()) {
            log.info("Tool execution denied: tool={}, reason={}", toolCall.name(), permResult.reason());
            return ToolResult.failure(toolCall.id(), permResult.reason());
        }

        // 3. 执行
        try {
            var result = executor.execute(toolCall);
            // 4. 审计执行结果
            interceptor.afterExecution(toolCall, context,
                    result.success() ? AuditEvent.RESULT_SUCCESS : AuditEvent.RESULT_FAILED);
            return result;
        } catch (Exception e) {
            log.error("Tool execution error: tool={}", toolCall.name(), e);
            interceptor.afterExecution(toolCall, context, AuditEvent.RESULT_ERROR + ": " + e.getMessage());
            return ToolResult.failure(toolCall.id(), "Execution error: " + e.getMessage());
        }
    }
}
