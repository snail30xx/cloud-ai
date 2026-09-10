package com.cloudai.security.config;

import com.cloudai.security.permission.OperationType;

import java.time.Duration;
import java.util.List;

/**
 * 安全模块配置属性 — 绑定 {@code cloud-ai.security} 命名空间。
 *
 * @author cloud-ai
 * @since 1.0
 */
public record SecurityProperties(Approval approval, Permission permission) {

    public SecurityProperties {
        if (approval == null) {
            approval = new Approval(Duration.ofSeconds(30), null);
        }
        if (permission == null) {
            permission = new Permission(List.of());
        }
    }

    public SecurityProperties() {
        this(new Approval(Duration.ofSeconds(30), null), new Permission(List.of()));
    }

    /**
     * 审批配置。
     *
     * @param timeout 审批等待超时
     * @param mode    审批模式（auto / manual，见 {@link com.cloudai.security.approval.ApprovalMode}）
     */
    public record Approval(Duration timeout, String mode) {
        public Approval {
            if (timeout == null) {
                timeout = Duration.ofSeconds(30);
            }
            if (mode == null || mode.isBlank()) {
                mode = "auto";
            }
        }
    }

    /** 权限规则配置。 */
    public record Permission(List<Rule> rules) {
        public Permission {
            rules = rules != null ? List.copyOf(rules) : List.of();
        }

        /** 单条权限规则。 */
        public record Rule(OperationType operation, String pattern, boolean allow) {
            public Rule {
                if (operation == null) {
                    throw new IllegalArgumentException("rule operation must not be null");
                }
                if (pattern == null || pattern.isBlank()) {
                    throw new IllegalArgumentException("rule pattern must not be blank");
                }
            }
        }
    }
}
