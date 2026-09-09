package com.cloudai.security.config;

import com.cloudai.security.impl.DefaultPermissionManager;
import com.cloudai.security.impl.InMemoryApprovalGateway;
import com.cloudai.security.impl.Slf4jAuditLogger;
import com.cloudai.security.impl.SecurityInterceptor;
import com.cloudai.security.model.PermissionResult;
import com.cloudai.security.spi.ApprovalGateway;
import com.cloudai.security.spi.AuditLogger;
import com.cloudai.security.spi.PermissionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityAutoConfiguration")
class SecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

    @Nested
    @DisplayName("default behavior (enabled by default)")
    class DefaultBehavior {
        @Test
        @DisplayName("should create all three beans")
        void shouldCreateAllBeans() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(PermissionManager.class);
                assertThat(context).hasSingleBean(ApprovalGateway.class);
                assertThat(context).hasSingleBean(AuditLogger.class);
                assertThat(context).hasSingleBean(SecurityProperties.class);
                assertThat(context).hasSingleBean(SecurityInterceptor.class);
            });
        }

        @Test
        @DisplayName("beans should be default implementations")
        void shouldBeDefaultImplementations() {
            contextRunner.run(context -> {
                assertThat(context.getBean(PermissionManager.class))
                        .isInstanceOf(DefaultPermissionManager.class);
                assertThat(context.getBean(ApprovalGateway.class))
                        .isInstanceOf(InMemoryApprovalGateway.class);
                assertThat(context.getBean(AuditLogger.class))
                        .isInstanceOf(Slf4jAuditLogger.class);
            });
        }
    }

    @Nested
    @DisplayName("module disabled")
    class ModuleDisabled {
        @Test
        @DisplayName("should not create any security beans when enabled=false")
        void shouldNotCreateBeansWhenDisabled() {
            contextRunner.withPropertyValues("cloud-ai.security.enabled=false").run(context -> {
                assertThat(context).doesNotHaveBean(PermissionManager.class);
                assertThat(context).doesNotHaveBean(ApprovalGateway.class);
                assertThat(context).doesNotHaveBean(AuditLogger.class);
                assertThat(context).doesNotHaveBean(SecurityInterceptor.class);
            });
        }
    }

    @Nested
    @DisplayName("bean replacement via ConditionalOnMissingBean")
    class BeanReplacement {
        @Test
        @DisplayName("should allow replacing default PermissionManager")
        void shouldAllowReplacingPermissionManager() {
            PermissionManager custom = (toolCall, context) -> PermissionResult.allow();
            contextRunner.withBean(PermissionManager.class, () -> custom).run(context -> {
                assertThat(context).hasSingleBean(PermissionManager.class);
                assertThat(context.getBean(PermissionManager.class)).isSameAs(custom);
            });
        }

        @Test
        @DisplayName("should allow replacing default ApprovalGateway")
        void shouldAllowReplacingApprovalGateway() {
            ApprovalGateway custom = request -> com.cloudai.security.model.ApprovalResponse.approved("custom");
            contextRunner.withBean(ApprovalGateway.class, () -> custom).run(context -> {
                assertThat(context).hasSingleBean(ApprovalGateway.class);
                assertThat(context.getBean(ApprovalGateway.class)).isSameAs(custom);
            });
        }

        @Test
        @DisplayName("should allow replacing default AuditLogger")
        void shouldAllowReplacingAuditLogger() {
            AuditLogger custom = new AuditLogger() {
                @Override public void logAccess(com.cloudai.security.model.AuditEvent event) {}
                @Override public void logDecision(com.cloudai.security.model.AuditEvent event) {}
                @Override public void logExecution(com.cloudai.security.model.AuditEvent event) {}
            };
            contextRunner.withBean(AuditLogger.class, () -> custom).run(context -> {
                assertThat(context).hasSingleBean(AuditLogger.class);
                assertThat(context.getBean(AuditLogger.class)).isSameAs(custom);
            });
        }
    }

    @Nested
    @DisplayName("configuration properties")
    class ConfigurationProperties {
        @Test
        @DisplayName("should use default timeout of 30s when not configured")
        void shouldUseDefaultTimeout() {
            contextRunner.run(context -> {
                var props = context.getBean(SecurityProperties.class);
                assertThat(props.approval().timeout()).isEqualTo(Duration.ofSeconds(30));
            });
        }
    }
}
