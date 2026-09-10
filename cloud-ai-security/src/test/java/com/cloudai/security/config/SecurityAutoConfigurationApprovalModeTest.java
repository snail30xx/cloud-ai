package com.cloudai.security.config;

import com.cloudai.security.approval.ApprovalGateway;
import com.cloudai.security.approval.InMemoryApprovalGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SecurityAutoConfiguration 审批模式解析")
class SecurityAutoConfigurationApprovalModeTest {

    @Test
    @DisplayName("mode=manual 装配 InMemoryApprovalGateway")
    void manualModeCreatesGateway() {
        var properties = new SecurityProperties(
                new SecurityProperties.Approval(Duration.ofSeconds(30), "manual"), null);

        var gateway = SecurityAutoConfiguration.approvalGateway(properties);

        assertInstanceOf(InMemoryApprovalGateway.class, gateway);
    }

    @Test
    @DisplayName("mode 缺省回退 auto")
    void nullModeDefaultsToAuto() {
        var properties = new SecurityProperties(
                new SecurityProperties.Approval(Duration.ofSeconds(30), null), null);

        var gateway = SecurityAutoConfiguration.approvalGateway(properties);

        assertInstanceOf(InMemoryApprovalGateway.class, gateway);
    }

    @Test
    @DisplayName("非法 mode fail fast，提示合法取值")
    void invalidModeFailsFast() {
        var properties = new SecurityProperties(
                new SecurityProperties.Approval(Duration.ofSeconds(30), "sometimes"), null);

        var ex = assertThrows(IllegalStateException.class,
                () -> SecurityAutoConfiguration.approvalGateway(properties));
        assert ex.getMessage().contains("auto") && ex.getMessage().contains("manual");
    }
}
