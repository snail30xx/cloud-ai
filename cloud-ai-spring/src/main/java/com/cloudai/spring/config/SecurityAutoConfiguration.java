package com.cloudai.spring.config;

import com.cloudai.security.model.OperationType;
import com.cloudai.security.spi.ApprovalGateway;
import com.cloudai.security.spi.AuditLogger;
import com.cloudai.security.spi.PermissionManager;
import com.cloudai.security.impl.SecurityInterceptor;
import com.cloudai.spring.properties.CloudAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public com.cloudai.security.config.SecurityProperties securityProperties(CloudAiProperties props) {
        var sec = props.security() != null ? props.security() : new CloudAiProperties.Security(null, null);
        var approvalTimeout = sec.approval() != null && sec.approval().timeout() != null
                ? sec.approval().timeout() : Duration.ofSeconds(30);

        List<com.cloudai.security.config.SecurityProperties.Permission.Rule> rules = new ArrayList<>();
        if (sec.permission() != null && sec.permission().rules() != null) {
            for (var r : sec.permission().rules()) {
                rules.add(new com.cloudai.security.config.SecurityProperties.Permission.Rule(
                        OperationType.valueOf(r.operation().toUpperCase()),
                        r.pattern(), r.allow()));
            }
        }
        return new com.cloudai.security.config.SecurityProperties(
                new com.cloudai.security.config.SecurityProperties.Approval(approvalTimeout),
                new com.cloudai.security.config.SecurityProperties.Permission(List.copyOf(rules)));
    }

    @Bean
    @ConditionalOnMissingBean
    public PermissionManager permissionManager(com.cloudai.security.config.SecurityProperties properties) {
        return com.cloudai.security.config.SecurityAutoConfiguration.permissionManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalGateway approvalGateway(com.cloudai.security.config.SecurityProperties properties) {
        return com.cloudai.security.config.SecurityAutoConfiguration.approvalGateway(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogger auditLogger() {
        return com.cloudai.security.config.SecurityAutoConfiguration.auditLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityInterceptor securityInterceptor(
            PermissionManager permissionManager,
            ApprovalGateway approvalGateway,
            AuditLogger auditLogger) {
        return com.cloudai.security.config.SecurityAutoConfiguration.securityInterceptor(
                permissionManager, approvalGateway, auditLogger);
    }
}