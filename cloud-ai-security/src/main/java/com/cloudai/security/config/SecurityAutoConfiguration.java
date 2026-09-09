package com.cloudai.security.config;

import com.cloudai.security.impl.DefaultPermissionManager;
import com.cloudai.security.impl.InMemoryApprovalGateway;
import com.cloudai.security.impl.SecurityInterceptor;
import com.cloudai.security.impl.Slf4jAuditLogger;
import com.cloudai.security.spi.ApprovalGateway;
import com.cloudai.security.spi.AuditLogger;
import com.cloudai.security.spi.PermissionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全模块自动装配。
 *
 * <p>通过 {@code cloud-ai.security.enabled} 控制（默认 true）。
 * 三个核心 Bean 均为 {@link ConditionalOnMissingBean}，允许外部替换。
 * {@link SecurityInterceptor} 自动编排三层安全链路。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnProperty(name = "cloud-ai.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PermissionManager permissionManager(SecurityProperties properties) {
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

    @Bean
    @ConditionalOnMissingBean
    public ApprovalGateway approvalGateway(SecurityProperties properties) {
        log.info("Creating InMemoryApprovalGateway with default timeout: {}", properties.approval().timeout());
        return new InMemoryApprovalGateway(properties.approval().timeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogger auditLogger() {
        log.info("Creating Slf4jAuditLogger");
        return new Slf4jAuditLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityInterceptor securityInterceptor(
            PermissionManager permissionManager,
            ApprovalGateway approvalGateway,
            AuditLogger auditLogger) {
        log.info("Creating SecurityInterceptor");
        return new SecurityInterceptor(permissionManager, approvalGateway, auditLogger);
    }
}
