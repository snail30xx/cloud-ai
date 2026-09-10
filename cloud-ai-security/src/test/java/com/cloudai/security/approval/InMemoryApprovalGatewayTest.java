package com.cloudai.security.approval;

import com.cloudai.security.approval.ApprovalRequest;
import com.cloudai.security.approval.ApprovalResponse;
import com.cloudai.security.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryApprovalGateway")
class InMemoryApprovalGatewayTest {

    private InMemoryApprovalGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new InMemoryApprovalGateway(Duration.ofSeconds(30));
    }

    @Nested
    @DisplayName("auto-approve low risk")
    class LowRisk {
        @Test
        @DisplayName("should auto-approve LOW risk without waiting")
        void shouldAutoApproveLowRisk() {
            var response = gateway.requestApproval(ApprovalRequest.lowRisk("FILE_READ"));
            assertTrue(response.approved());
            assertTrue(response.reason().contains("low risk"));
        }
    }

    @Nested
    @DisplayName("manual approval")
    class ManualApproval {
        @Test
        @DisplayName("should timeout when no approval given within timeout")
        void shouldTimeoutWithoutApproval() {
            var response = gateway.requestApproval(
                    new ApprovalRequest("FILE_WRITE", RiskLevel.MEDIUM, null, Duration.ofMillis(100), null));
            assertFalse(response.approved());
            assertTrue(response.reason().toLowerCase().contains("timeout"));
        }

        @Test
        @DisplayName("should complete via approve() within timeout")
        void shouldApproveWhenApproved() throws Exception {
            var executor = Executors.newSingleThreadExecutor();
            var responseRef = new java.util.concurrent.atomic.AtomicReference<ApprovalResponse>();

            var latch = new CountDownLatch(1);
            executor.submit(() -> {
                responseRef.set(gateway.requestApproval(
                        new ApprovalRequest("FILE_WRITE", RiskLevel.MEDIUM, null, Duration.ofSeconds(5), null)));
                latch.countDown();
            });

            // Wait for the request to be registered
            Thread.sleep(200);
            assertEquals(1, gateway.pendingCount());

            // Get the pending request ID via listPending() and approve it
            var pendingId = gateway.listPending().keySet().iterator().next();
            assertTrue(gateway.approve(pendingId, "admin"));

            // Wait for the response
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            var response = responseRef.get();
            assertNotNull(response);
            assertTrue(response.approved());
            assertEquals("admin", response.approver());

            executor.shutdown();
        }

        @Test
        @DisplayName("should complete via deny() within timeout")
        void shouldCompleteViaDeny() throws Exception {
            var executor = Executors.newSingleThreadExecutor();
            var responseRef = new java.util.concurrent.atomic.AtomicReference<ApprovalResponse>();

            var latch = new CountDownLatch(1);
            executor.submit(() -> {
                responseRef.set(gateway.requestApproval(
                        new ApprovalRequest("SHELL_EXEC", RiskLevel.HIGH, null, Duration.ofSeconds(5), null)));
                latch.countDown();
            });

            Thread.sleep(200);
            assertEquals(1, gateway.pendingCount());

            var pendingId = gateway.listPending().keySet().iterator().next();
            assertTrue(gateway.deny(pendingId, "Too risky"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            var response = responseRef.get();
            assertNotNull(response);
            assertFalse(response.approved());
            assertEquals("Too risky", response.reason());

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("approval mode")
    class ApprovalModeBehavior {

        @Test
        @DisplayName("MANUAL 模式下 LOW 风险也等待人工审批")
        void manualModeLowRiskWaits() {
            var manualGateway = new InMemoryApprovalGateway(
                    Duration.ofSeconds(30), ApprovalMode.MANUAL);

            var response = manualGateway.requestApproval(
                    new ApprovalRequest("FILE_READ", RiskLevel.LOW, null, Duration.ofMillis(100), null));

            assertFalse(response.approved());
            assertTrue(response.reason().toLowerCase().contains("timeout"));
        }

        @Test
        @DisplayName("AUTO 模式下 LOW 风险自动放行（默认行为不变）")
        void autoModeLowRiskAutoApproves() {
            var autoGateway = new InMemoryApprovalGateway(
                    Duration.ofSeconds(30), ApprovalMode.AUTO);

            var response = autoGateway.requestApproval(ApprovalRequest.lowRisk("FILE_READ"));

            assertTrue(response.approved());
        }
    }

    @Nested
    @DisplayName("approve/deny by ID")
    class ApproveDeny {
        @Test
        @DisplayName("approve() should return false for non-existent request")
        void approveNonExistent() {
            assertFalse(gateway.approve("non-existent", "admin"));
        }

        @Test
        @DisplayName("deny() should return false for non-existent request")
        void denyNonExistent() {
            assertFalse(gateway.deny("non-existent", "too risky"));
        }

        @Test
        @DisplayName("pendingCount should be zero initially")
        void pendingCountZero() {
            assertEquals(0, gateway.pendingCount());
        }
    }

    @Nested
    @DisplayName("risk level behavior")
    class RiskLevelBehavior {
        @Test
        @DisplayName("LOW risk should auto-approve")
        void lowRiskAutoApproves() {
            var response = gateway.requestApproval(ApprovalRequest.of("FILE_READ", RiskLevel.LOW));
            assertTrue(response.approved());
        }

        @Test
        @DisplayName("MEDIUM risk should wait for approval")
        void mediumRiskWaits() {
            var response = gateway.requestApproval(
                    new ApprovalRequest("FILE_WRITE", RiskLevel.MEDIUM, null, Duration.ofMillis(50), null));
            // Should timeout since no one approves
            assertFalse(response.approved());
        }

        @Test
        @DisplayName("HIGH risk should wait for approval")
        void highRiskWaits() {
            var response = gateway.requestApproval(
                    new ApprovalRequest("SHELL_EXEC", RiskLevel.HIGH, null, Duration.ofMillis(50), null));
            // Should timeout since no one approves
            assertFalse(response.approved());
        }
    }
}
