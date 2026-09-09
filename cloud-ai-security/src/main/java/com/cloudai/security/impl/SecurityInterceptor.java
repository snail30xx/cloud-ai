package com.cloudai.security.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.security.model.AuditEvent;
import com.cloudai.security.model.ApprovalRequest;
import com.cloudai.security.model.OperationType;
import com.cloudai.security.model.PermissionResult;
import com.cloudai.security.model.RiskLevel;
import com.cloudai.security.model.SecurityContext;
import com.cloudai.security.spi.ApprovalGateway;
import com.cloudai.security.spi.AuditLogger;
import com.cloudai.security.spi.PermissionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全编排器 — 串联 {@link PermissionManager}、{@link ApprovalGateway}、{@link AuditLogger} 三层安全链路。
 *
 * <p>调用方只需在工具执行前后分别调用 {@link #beforeExecution} 和 {@link #afterExecution}，
 * 拦截器内部自动完成权限校验、风险审批和审计记录。</p>
 *
 * <p>流程：
 * <ol>
 *   <li>{@link #beforeExecution} — 记录访问 → 权限校验 → 风险评估 → 审批（如需）→ 记录决策</li>
 *   <li>调用方执行工具</li>
 *   <li>{@link #afterExecution} — 记录执行结果</li>
 * </ol>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class SecurityInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);

    private final PermissionManager permissionManager;
    private final ApprovalGateway approvalGateway;
    private final AuditLogger auditLogger;

    public SecurityInterceptor(PermissionManager permissionManager,
                               ApprovalGateway approvalGateway,
                               AuditLogger auditLogger) {
        this.permissionManager = permissionManager;
        this.approvalGateway = approvalGateway;
        this.auditLogger = auditLogger;
    }

    /**
     * 工具执行前拦截：访问记录 → 权限校验 → 风险评估 → 审批 → 决策记录。
     *
     * @param toolCall 待执行的工具调用
     * @param context  安全上下文
     * @return {@code allowed=true} 表示可以执行，{@code allowed=false} 表示拒绝
     */
    public PermissionResult beforeExecution(ToolCall toolCall, SecurityContext context) {
        var opType = OperationType.from(toolCall.name());
        var target = DefaultPermissionManager.extractTarget(toolCall);
        var agentId = context.agentId();

        // 1. 记录访问
        auditLogger.logAccess(AuditEvent.of(agentId, opType.name(), target, "ACCESSED"));

        // 2. 权限校验
        var permResult = permissionManager.check(toolCall, context);
        if (!permResult.allowed()) {
            log.info("Permission denied for tool={}, target={}, agent={}", toolCall.name(), target, agentId);
            auditLogger.logDecision(AuditEvent.of(agentId, opType.name(), target, AuditEvent.RESULT_DENIED));
            return permResult;
        }

        // 3. 风险评估 + 审批
        var riskLevel = RiskLevel.from(opType);
        if (riskLevel != RiskLevel.LOW) {
            log.debug("Risk assessment: operation={}, risk={}, requesting approval", opType, riskLevel);
            var approvalRequest = ApprovalRequest.of(toolCall, riskLevel);
            var approvalResponse = approvalGateway.requestApproval(approvalRequest);
            if (!approvalResponse.approved()) {
                log.info("Approval denied for tool={}, target={}, agent={}: {}",
                        toolCall.name(), target, agentId, approvalResponse.reason());
                auditLogger.logDecision(AuditEvent.of(agentId, opType.name(), target, AuditEvent.RESULT_DENIED));
                return PermissionResult.deny("Approval denied: " + approvalResponse.reason());
            }
        }

        // 4. 记录允许决策
        auditLogger.logDecision(AuditEvent.of(agentId, opType.name(), target, AuditEvent.RESULT_ALLOWED));
        return permResult;
    }

    /**
     * 工具执行后审计记录。
     *
     * @param toolCall 已执行的工具调用
     * @param context  安全上下文
     * @param result   执行结果（如 {@code "SUCCESS"}、{@code "FAILED"}、{@code "ERROR: ..."}）
     */
    public void afterExecution(ToolCall toolCall, SecurityContext context, String result) {
        var opType = OperationType.from(toolCall.name());
        var target = DefaultPermissionManager.extractTarget(toolCall);
        auditLogger.logExecution(AuditEvent.of(context.agentId(), opType.name(), target, result));
    }
}
