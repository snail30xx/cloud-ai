package com.cloudai.security.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.security.model.AuditEvent;
import com.cloudai.security.model.OperationType;
import com.cloudai.security.model.PermissionResult;
import com.cloudai.security.model.RiskLevel;
import com.cloudai.security.model.SecurityContext;
import com.cloudai.security.spi.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityInterceptor")
class SecurityInterceptorTest {

    private DefaultPermissionManager permissionManager;
    private InMemoryApprovalGateway approvalGateway;
    private List<AuditEvent> captured;
    private AuditLogger auditLogger;
    private SecurityInterceptor interceptor;
    private SecurityContext ctx;

    @BeforeEach
    void setUp() {
        permissionManager = new DefaultPermissionManager();
        approvalGateway = new InMemoryApprovalGateway(Duration.ofMillis(500));
        captured = new ArrayList<>();
        auditLogger = capturingLogger(captured);
        interceptor = new SecurityInterceptor(permissionManager, approvalGateway, auditLogger);
        ctx = SecurityContext.of("agent1");
    }

    @Nested
    @DisplayName("beforeExecution: allow chain")
    class AllowChain {
        @Test
        @DisplayName("low-risk allowed operation should pass through")
        void lowRiskAllowed() {
            permissionManager.allow(OperationType.FILE_READ, "/workspace/**");

            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}");
            var result = interceptor.beforeExecution(toolCall, ctx);

            assertTrue(result.allowed());
            assertEquals(2, captured.size());
            assertEquals("ACCESSED", captured.get(0).result());
            assertEquals("ALLOWED", captured.get(1).result());
        }
    }

    @Nested
    @DisplayName("beforeExecution: deny chain")
    class DenyChain {
        @Test
        @DisplayName("permission denied should not trigger approval")
        void permissionDeniedSkipsApproval() {
            // No rules configured — deny all
            var toolCall = new ToolCall("call_1", "file_write", "/etc/hosts");
            var result = interceptor.beforeExecution(toolCall, ctx);

            assertFalse(result.allowed());
            assertEquals(2, captured.size());
            assertEquals("ACCESSED", captured.get(0).result());
            assertEquals("DENIED", captured.get(1).result());
        }

        @Test
        @DisplayName("blacklist denial should be audited")
        void blacklistDenialAudited() {
            permissionManager.allow(OperationType.SHELL_EXEC, "/safe/**");
            permissionManager.deny(OperationType.SHELL_EXEC, "/safe/rm/**");

            var toolCall = new ToolCall("call_1", "shell_exec", "{\"command\":\"/safe/rm/force\"}");
            var result = interceptor.beforeExecution(toolCall, ctx);

            assertFalse(result.allowed());
            assertEquals("DENIED", captured.get(1).result());
        }
    }

    @Nested
    @DisplayName("beforeExecution: approval flow")
    class ApprovalFlow {
        @Test
        @DisplayName("high-risk operation should timeout without approval")
        void highRiskTimeout() {
            permissionManager.allow(OperationType.SHELL_EXEC, "/safe/**");

            var toolCall = new ToolCall("call_1", "shell_exec", "{\"command\":\"/safe/process\"}");
            var result = interceptor.beforeExecution(toolCall, ctx);

            assertFalse(result.allowed());
            assertFalse(result.allowed());
            assertTrue(result.reason().contains("Approval"));
            assertEquals("DENIED", captured.get(1).result());
        }

        @Test
        @DisplayName("high-risk operation should pass when approved")
        void highRiskApproved() throws Exception {
            permissionManager.allow(OperationType.SHELL_EXEC, "/safe/**");

            var latch = new CountDownLatch(1);
            var resultRef = new java.util.concurrent.atomic.AtomicReference<PermissionResult>();
            var executor = Executors.newSingleThreadExecutor();

            var toolCall = new ToolCall("call_1", "shell_exec", "{\"command\":\"/safe/process\"}");

            executor.submit(() -> {
                resultRef.set(interceptor.beforeExecution(toolCall, ctx));
                latch.countDown();
            });

            // Wait for approval request to be registered
            Thread.sleep(200);
            assertEquals(1, approvalGateway.pendingCount());

            // Approve the pending request
            var pendingId = approvalGateway.listPending().keySet().iterator().next();
            assertTrue(approvalGateway.approve(pendingId, "admin"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            var result = resultRef.get();
            assertNotNull(result);
            assertTrue(result.allowed());

            // Verify audit: access, allowed decision
            assertEquals(2, captured.size());
            assertEquals("ACCESSED", captured.get(0).result());
            assertEquals("ALLOWED", captured.get(1).result());

            executor.shutdown();
        }

        @Test
        @DisplayName("approval request should carry ToolCall")
        void approvalRequestCarriesToolCall() throws Exception {
            permissionManager.allow(OperationType.SHELL_EXEC, "/safe/**");

            var latch = new CountDownLatch(1);
            var executor = Executors.newSingleThreadExecutor();
            var toolCall = new ToolCall("call_1", "shell_exec", "{\"command\":\"/safe/run\"}");

            executor.submit(() -> {
                interceptor.beforeExecution(toolCall, ctx);
                latch.countDown();
            });

            Thread.sleep(200);

            // Verify the pending approval request carries the ToolCall
            var pending = approvalGateway.listPending();
            assertEquals(1, pending.size());
            var approvalRequest = pending.values().iterator().next();
            assertNotNull(approvalRequest.toolCall());
            assertEquals("shell_exec", approvalRequest.toolCall().name());
            assertEquals("{\"command\":\"/safe/run\"}", approvalRequest.toolCall().arguments());
            assertEquals(RiskLevel.HIGH, approvalRequest.risk());

            // Clean up
            var pendingId = pending.keySet().iterator().next();
            approvalGateway.deny(pendingId, "test cleanup");
            assertTrue(latch.await(2, TimeUnit.SECONDS));

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("afterExecution")
    class AfterExecution {
        @Test
        @DisplayName("should log execution result")
        void shouldLogExecutionResult() {
            permissionManager.allow(OperationType.FILE_READ, "/workspace/**");

            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}");
            interceptor.beforeExecution(toolCall, ctx);
            interceptor.afterExecution(toolCall, ctx, AuditEvent.RESULT_SUCCESS);

            // access + decision + execution = 3 events
            assertEquals(3, captured.size());
            assertEquals("ACCESSED", captured.get(0).result());
            assertEquals("ALLOWED", captured.get(1).result());
            assertEquals("SUCCESS", captured.get(2).result());
        }

        @Test
        @DisplayName("should log failed execution")
        void shouldLogFailedExecution() {
            permissionManager.allow(OperationType.FILE_READ, "/workspace/**");

            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}");
            interceptor.beforeExecution(toolCall, ctx);
            interceptor.afterExecution(toolCall, ctx, AuditEvent.RESULT_FAILED);

            assertEquals(3, captured.size());
            assertEquals("FAILED", captured.get(2).result());
        }
    }

    private static AuditLogger capturingLogger(List<AuditEvent> captured) {
        return new AuditLogger() {
            @Override public void logAccess(AuditEvent event) { captured.add(event); }
            @Override public void logDecision(AuditEvent event) { captured.add(event); }
            @Override public void logExecution(AuditEvent event) { captured.add(event); }
        };
    }
}
