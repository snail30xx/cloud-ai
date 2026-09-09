package com.cloudai.execution.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.core.model.ToolDefinition;
import com.cloudai.execution.model.ToolResult;
import com.cloudai.security.spi.AuditLogger;
import com.cloudai.execution.spi.ToolExecutor;
import com.cloudai.security.impl.DefaultPermissionManager;
import com.cloudai.security.impl.InMemoryApprovalGateway;
import com.cloudai.security.impl.SecurityInterceptor;
import com.cloudai.security.impl.Slf4jAuditLogger;
import com.cloudai.security.model.AuditEvent;
import com.cloudai.security.model.OperationType;
import com.cloudai.security.model.SecurityContext;
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

@DisplayName("ToolExecutionService")
class ToolExecutionServiceTest {

    private DefaultToolRegistry registry;
    private DefaultPermissionManager permissionManager;
    private InMemoryApprovalGateway approvalGateway;
    private List<AuditEvent> captured;
    private SecurityInterceptor interceptor;
    private ToolExecutionService service;
    private SecurityContext ctx;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        permissionManager = new DefaultPermissionManager();
        approvalGateway = new InMemoryApprovalGateway(Duration.ofMillis(500));
        captured = new ArrayList<>();
        AuditLogger logger = capturingLogger(captured);
        interceptor = new SecurityInterceptor(permissionManager, approvalGateway, logger);
        service = new ToolExecutionService(registry, interceptor);
        ctx = SecurityContext.of("agent1");
    }

    @Nested
    @DisplayName("tool not found")
    class ToolNotFound {
        @Test
        @DisplayName("should return failure when tool not registered")
        void shouldFailWhenNotRegistered() {
            var result = service.execute(
                    new ToolCall("call_1", "nonexistent", "{}"), ctx);

            assertFalse(result.success());
            assertTrue(result.error().contains("not found"));
        }
    }

    @Nested
    @DisplayName("permission denied")
    class PermissionDenied {
        @Test
        @DisplayName("should deny execution when no permission rules configured")
        void shouldDenyWhenNoRules() {
            registry.register(new ToolDefinition("file_read", "read", Map.of()),
                    call -> ToolResult.success(call.id(), "content"));

            var result = service.execute(
                    new ToolCall("call_1", "file_read", "{\"path\":\"/etc/hosts\"}"), ctx);

            assertFalse(result.success());
            assertTrue(result.error().contains("deny"));
        }
    }

    @Nested
    @DisplayName("allowed execution")
    class AllowedExecution {
        @Test
        @DisplayName("should execute when permission allows and risk is LOW")
        void shouldExecuteWhenAllowed() {
            permissionManager.allow(OperationType.FILE_READ, "/workspace/**");
            registry.register(new ToolDefinition("file_read", "read", Map.of()),
                    call -> ToolResult.success(call.id(), "file content"));

            var result = service.execute(
                    new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}"), ctx);

            assertTrue(result.success());
            assertEquals("file content", result.output());
            // access + decision (allowed) + execution (success) = 3 events
            assertEquals(3, captured.size());
        }

        @Test
        @DisplayName("should audit failure when tool throws exception")
        void shouldAuditFailureOnException() {
            permissionManager.allow(OperationType.FILE_READ, "/workspace/**");
            registry.register(new ToolDefinition("file_read", "read", Map.of()),
                    call -> { throw new RuntimeException("disk error"); });

            var result = service.execute(
                    new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}"), ctx);

            assertFalse(result.success());
            assertTrue(result.error().contains("disk error"));
            // access + decision (allowed) + execution (error) = 3 events
            assertEquals(3, captured.size());
            assertEquals("ERROR", captured.get(2).result().split(":")[0]);
        }
    }

    @Nested
    @DisplayName("high-risk approval flow")
    class HighRiskApproval {
        @Test
        @DisplayName("should timeout when high-risk operation not approved")
        void shouldTimeoutWhenNotApproved() {
            permissionManager.allow(OperationType.SHELL_EXEC, "/safe/**");
            registry.register(new ToolDefinition("shell_exec", "exec", Map.of()),
                    call -> ToolResult.success(call.id(), "done"));

            var result = service.execute(
                    new ToolCall("call_1", "shell_exec", "{\"command\":\"/safe/process\"}"), ctx);

            assertFalse(result.success());
            assertTrue(result.error().contains("Approval"));
        }

        @Test
        @DisplayName("should execute when high-risk operation is approved")
        void shouldExecuteWhenApproved() throws Exception {
            permissionManager.allow(OperationType.SHELL_EXEC, "/safe/**");
            registry.register(new ToolDefinition("shell_exec", "exec", Map.of()),
                    call -> ToolResult.success(call.id(), "command output"));

            var latch = new CountDownLatch(1);
            var resultRef = new java.util.concurrent.atomic.AtomicReference<ToolResult>();
            var executor = Executors.newSingleThreadExecutor();

            executor.submit(() -> {
                resultRef.set(service.execute(
                        new ToolCall("call_1", "shell_exec", "{\"command\":\"/safe/process\"}"), ctx));
                latch.countDown();
            });

            Thread.sleep(200);
            assertEquals(1, approvalGateway.pendingCount());

            var pendingId = approvalGateway.listPending().keySet().iterator().next();
            assertTrue(approvalGateway.approve(pendingId, "admin"));

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            var result = resultRef.get();
            assertNotNull(result);
            assertTrue(result.success());
            assertEquals("command output", result.output());

            executor.shutdown();
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
