package com.cloudai.security.config;

import com.cloudai.security.permission.DefaultPermissionManager;
import com.cloudai.security.approval.ApprovalMode;
import com.cloudai.security.approval.InMemoryApprovalGateway;
import com.cloudai.security.SecurityInterceptor;
import com.cloudai.security.audit.Slf4jAuditLogger;
import com.cloudai.security.approval.ApprovalGateway;
import com.cloudai.security.audit.AuditLogger;
import com.cloudai.security.permission.PermissionManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 安全模块工厂 — 替代 Spring 自动装配。
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class SecurityAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    private SecurityAutoConfiguration() {}

    public static PermissionManager permissionManager(SecurityProperties properties) {
        var manager = new DefaultPermissionManager();
        for (var rule : properties.permission().rules()) {
            if (rule.allow()) {
                manager.allow(rule.operation(), rule.pattern());
            } else {
                manager.deny(rule.operation(), rule.pattern());
            }
        }
        log.info("Creating DefaultPermissionManager with {} rule(s)", properties.permission().rules().size());
        return manager;
    }

    public static ApprovalGateway approvalGateway(SecurityProperties properties) {
        var mode = parseApprovalMode(properties.approval().mode());
        log.info("Creating InMemoryApprovalGateway: mode={}, default timeout={}",
                mode, properties.approval().timeout());
        return new InMemoryApprovalGateway(properties.approval().timeout(), mode);
    }

    /** 解析审批模式字符串，非法值 fail fast。 */
    private static ApprovalMode parseApprovalMode(@Nullable String mode) {
        var normalized = mode != null ? mode.trim().toUpperCase(java.util.Locale.ROOT) : "AUTO";
        return switch (normalized) {
            case "AUTO" -> ApprovalMode.AUTO;
            case "MANUAL" -> ApprovalMode.MANUAL;
            default -> throw new IllegalStateException(
                    "Invalid cloud-ai.security.approval.mode '" + mode + "', valid values: auto, manual");
        };
    }

    public static AuditLogger auditLogger() {
        log.info("Creating Slf4jAuditLogger");
        return new Slf4jAuditLogger();
    }

    public static SecurityInterceptor securityInterceptor(
            PermissionManager permissionManager,
            ApprovalGateway approvalGateway,
            AuditLogger auditLogger) {
        log.info("Creating SecurityInterceptor");
        return new SecurityInterceptor(permissionManager, approvalGateway, auditLogger);
    }
}