package com.cloudai.security;

import com.cloudai.core.model.ToolCall;
import com.cloudai.security.impl.DefaultPermissionManager;
import com.cloudai.security.impl.InMemoryApprovalGateway;
import com.cloudai.security.model.ApprovalRequest;
import com.cloudai.security.model.ApprovalResponse;
import com.cloudai.security.model.AuditEvent;
import com.cloudai.security.model.OperationType;
import com.cloudai.security.model.RiskLevel;
import com.cloudai.security.model.SecurityContext;
import com.cloudai.security.spi.AuditLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Security Integration: Permission -> Approval -> Audit")
class SecurityIntegrationTest {

    @Test
    @DisplayName("denied permission should be audited as denied decision")
    void deniedPermissionShouldBeAudited() {
        var captured = new ArrayList<AuditEvent>();
        AuditLogger logger = capturingLogger(captured);

        var manager = new DefaultPermissionManager(); // deny-all
        var ctx = SecurityContext.of("agent1");
        var toolCall = new ToolCall("call_1", "file_write", "/etc/hosts");

        var result = manager.check(toolCall, ctx);
        assertFalse(result.allowed());

        logger.logDecision(AuditEvent.of(ctx.agentId(), "FILE_WRITE", "/etc/hosts", AuditEvent.RESULT_DENIED));
        assertEquals(1, captured.size());
        assertEquals(AuditEvent.RESULT_DENIED, captured.get(0).result());
    }

    @Test
    @DisplayName("low-risk approval should auto-approve and be audited as success")
    void lowRiskApprovalShouldAutoApproveAndAudit() {
        var captured = new ArrayList<AuditEvent>();
        AuditLogger logger = capturingLogger(captured);

        var gateway = new InMemoryApprovalGateway(Duration.ofSeconds(30));

        var request = ApprovalRequest.lowRisk("FILE_READ");
        var response = gateway.requestApproval(request);
        assertTrue(response.approved());

        logger.logExecution(AuditEvent.of("agent1", "FILE_READ", "/data.txt", AuditEvent.RESULT_SUCCESS));
        assertEquals(1, captured.size());
        assertEquals(AuditEvent.RESULT_SUCCESS, captured.get(0).result());
    }

    @Test
    @DisplayName("high-risk approval should timeout and be audited as denied")
    void highRiskApprovalShouldTimeoutAndAudit() {
        var captured = new ArrayList<AuditEvent>();
        AuditLogger logger = capturingLogger(captured);

        var gateway = new InMemoryApprovalGateway(Duration.ofSeconds(30));

        var request = new ApprovalRequest("SHELL_EXEC", RiskLevel.HIGH, null, Duration.ofMillis(100), null);
        var response = gateway.requestApproval(request);
        assertFalse(response.approved());

        logger.logDecision(AuditEvent.of("agent1", "SHELL_EXEC", "rm -rf /", AuditEvent.RESULT_DENIED));
        assertEquals(1, captured.size());
        assertEquals(AuditEvent.RESULT_DENIED, captured.get(0).result());
    }

    @Test
    @DisplayName("full chain: permission allows -> approval auto-approves -> execution audited")
    void fullChainAllowApproveAndAudit() {
        var captured = new ArrayList<AuditEvent>();
        AuditLogger logger = capturingLogger(captured);

        var manager = new DefaultPermissionManager()
                .allow(OperationType.FILE_READ, "/workspace/**");
        var gateway = new InMemoryApprovalGateway(Duration.ofSeconds(30));
        var ctx = SecurityContext.of("agent1");

        // 1. Permission check
        var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}");
        var permResult = manager.check(toolCall, ctx);
        assertTrue(permResult.allowed());

        logger.logAccess(AuditEvent.of(ctx.agentId(), "FILE_READ", "/workspace/data.txt",
                AuditEvent.RESULT_ALLOWED));

        // 2. Approval (low-risk auto-approves)
        var approvalResponse = gateway.requestApproval(ApprovalRequest.lowRisk("FILE_READ"));
        assertTrue(approvalResponse.approved());

        // 3. Execution audit
        logger.logExecution(AuditEvent.of(ctx.agentId(), "FILE_READ", "/workspace/data.txt",
                AuditEvent.RESULT_SUCCESS));

        assertEquals(2, captured.size());
        assertEquals(AuditEvent.RESULT_ALLOWED, captured.get(0).result());
        assertEquals(AuditEvent.RESULT_SUCCESS, captured.get(1).result());
    }

    @Test
    @DisplayName("full chain: permission denies -> approval timeout -> audit denied")
    void fullChainDenyTimeoutAndAudit() {
        var captured = new ArrayList<AuditEvent>();
        AuditLogger logger = capturingLogger(captured);

        var manager = new DefaultPermissionManager()
                .allow(OperationType.SHELL_EXEC, "/safe/**")
                .deny(OperationType.SHELL_EXEC, "/safe/rm/**");
        var gateway = new InMemoryApprovalGateway(Duration.ofSeconds(30));
        var ctx = SecurityContext.of("agent1");

        // 1. Permission check - denied by blacklist
        var toolCall = new ToolCall("call_1", "shell_exec", "{\"command\":\"/safe/rm/force\"}");
        var permResult = manager.check(toolCall, ctx);
        assertFalse(permResult.allowed());

        logger.logDecision(AuditEvent.of(ctx.agentId(), "SHELL_EXEC", "/safe/rm/force",
                AuditEvent.RESULT_DENIED));

        // 2. Even if permission denied, simulate approval request for audit
        var request = new ApprovalRequest("SHELL_EXEC", RiskLevel.HIGH, null, Duration.ofMillis(100), null);
        var response = gateway.requestApproval(request);
        assertFalse(response.approved());

        logger.logExecution(AuditEvent.of(ctx.agentId(), "SHELL_EXEC", "/safe/rm/force",
                AuditEvent.RESULT_DENIED));

        assertEquals(2, captured.size());
        assertEquals(AuditEvent.RESULT_DENIED, captured.get(0).result());
        assertEquals(AuditEvent.RESULT_DENIED, captured.get(1).result());
    }

    @Test
    @DisplayName("manual approval flow: request -> approve via listPending -> audit success")
    void manualApprovalFlow() throws Exception {
        var captured = new ArrayList<AuditEvent>();
        AuditLogger logger = capturingLogger(captured);

        var gateway = new InMemoryApprovalGateway(Duration.ofSeconds(30));
        var ctx = SecurityContext.of("agent1");

        var latch = new CountDownLatch(1);
        var responseRef = new java.util.concurrent.atomic.AtomicReference<ApprovalResponse>();
        var executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

        executor.submit(() -> {
            responseRef.set(gateway.requestApproval(
                    new ApprovalRequest("FILE_WRITE", RiskLevel.MEDIUM,
                            Map.of("path", "/workspace/output.txt"), Duration.ofSeconds(5), null)));
            latch.countDown();
        });

        // Wait for the request to be registered
        Thread.sleep(200);
        assertEquals(1, gateway.pendingCount());

        // Approve via listPending
        var pendingId = gateway.listPending().keySet().iterator().next();
        assertTrue(gateway.approve(pendingId, "admin"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        var response = responseRef.get();
        assertTrue(response.approved());
        assertEquals("admin", response.approver());

        logger.logExecution(AuditEvent.of(ctx.agentId(), "FILE_WRITE", "/workspace/output.txt",
                AuditEvent.RESULT_SUCCESS));

        assertEquals(1, captured.size());
        assertEquals(AuditEvent.RESULT_SUCCESS, captured.get(0).result());

        executor.shutdown();
    }

    private static AuditLogger capturingLogger(List<AuditEvent> captured) {
        return new AuditLogger() {
            @Override public void logAccess(AuditEvent event) { captured.add(event); }
            @Override public void logDecision(AuditEvent event) { captured.add(event); }
            @Override public void logExecution(AuditEvent event) { captured.add(event); }
        };
    }
}

