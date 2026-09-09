package com.cloudai.security.config;

import com.cloudai.security.model.OperationType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 安全模块配置属性 — 绑定 {@code cloud-ai.security} 命名空间。
 *
 * @author cloud-ai
 * @since 1.0
 */
@ConfigurationProperties("cloud-ai.security")
public record SecurityProperties(Approval approval, Permission permission) {

    public SecurityProperties {
        if (approval == null) {
            approval = new Approval(Duration.ofSeconds(30));
        }
        if (permission == null) {
            permission = new Permission(List.of());
        }
    }

    public SecurityProperties() {
        this(new Approval(Duration.ofSeconds(30)), new Permission(List.of()));
    }

    /** 审批配置。 */
    public record Approval(Duration timeout) {
        public Approval {
            if (timeout == null) {
                timeout = Duration.ofSeconds(30);
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
