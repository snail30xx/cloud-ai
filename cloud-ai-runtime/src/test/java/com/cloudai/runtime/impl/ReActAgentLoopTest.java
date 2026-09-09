package com.cloudai.runtime.impl;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.ChatResponse;
import com.cloudai.core.model.FinishReason;
import com.cloudai.core.model.Message;
import com.cloudai.core.model.TokenUsage;
import com.cloudai.core.model.ToolCall;
import com.cloudai.core.model.ToolDefinition;
import com.cloudai.core.spi.ChatModel;
import com.cloudai.execution.impl.DefaultToolRegistry;
import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.model.ToolResult;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.model.AgentRequest;
import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.runtime.spi.StopCondition;
import com.cloudai.security.impl.DefaultPermissionManager;
import com.cloudai.security.impl.SecurityInterceptor;
import com.cloudai.security.impl.Slf4jAuditLogger;
import com.cloudai.security.model.OperationType;
import com.cloudai.security.spi.ApprovalGateway;
import com.cloudai.security.model.ApprovalResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReActAgentLoop")
class ReActAgentLoopTest {

    private StubChatModel chatModel;
    private ModelRouter router;
    private DefaultToolRegistry registry;
    private ToolExecutionService executionService;
    private ReActAgentLoop agentLoop;

    @BeforeEach
    void setUp() {
        chatModel = new StubChatModel();
        router = new ModelRouter("stub");
        router.register("stub", chatModel);

        registry = new DefaultToolRegistry();

        var permissionManager = new DefaultPermissionManager();
        permissionManager.allow(OperationType.FILE_READ, "/**");
        permissionManager.allow(OperationType.FILE_WRITE, "/**");
        permissionManager.allow(OperationType.SHELL_EXEC, "/**");
        permissionManager.allow(OperationType.CUSTOM, "/**");

        ApprovalGateway autoApprove = request -> ApprovalResponse.approved("auto-approved");
        var auditLogger = new Slf4jAuditLogger();
        var interceptor = new SecurityInterceptor(permissionManager, autoApprove, auditLogger);
        executionService = new ToolExecutionService(registry, interceptor);

        agentLoop = new ReActAgentLoop(router, registry, executionService, 10,
                Duration.ofMinutes(5), List.of());
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("LLM direct answer -> COMPLETED")
        void directAnswer() {
            chatModel.enqueue(textResponse("Hello!", new TokenUsage(10, 5)));

            var result = agentLoop.run(AgentRequest.of("Hi"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals("Hello!", result.content());
            assertEquals(1, result.turnsExecuted());
            assertEquals(0, result.toolCallsExecuted());
            assertEquals(15, result.totalUsage().totalTokens());
            assertNotNull(result.traceId());
        }

        @Test
        @DisplayName("single tool call -> COMPLETED, toolCallsExecuted=1")
        void singleToolCall() {
            registerEchoTool("file_read");
            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(textResponse("File content: hello", new TokenUsage(15, 10)));

            var result = agentLoop.run(AgentRequest.of("Read the file"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals("File content: hello", result.content());
            assertEquals(2, result.turnsExecuted());
            assertEquals(1, result.toolCallsExecuted());
        }

        @Test
        @DisplayName("system prompt injected")
        void systemPromptInjected() {
            chatModel.enqueue(textResponse("ok", new TokenUsage(5, 5)));

            var result = agentLoop.run(AgentRequest.of("Hi", "You are a bot"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals("system", result.history().get(0).role());
            assertEquals("You are a bot", result.history().get(0).content());
        }

        @Test
        @DisplayName("multiple tool calls -> toolCallsExecuted=2")
        void multipleToolCalls() {
            registerEchoTool("file_read");
            var call1 = new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/a.txt\"}");
            var call2 = new ToolCall("call_2", "file_read", "{\"path\":\"/workspace/b.txt\"}");
            chatModel.enqueue(toolCallResponse(List.of(call1, call2), new TokenUsage(10, 5)));
            chatModel.enqueue(textResponse("Done", new TokenUsage(15, 10)));

            var result = agentLoop.run(AgentRequest.of("Read both files"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals(2, result.turnsExecuted());
            assertEquals(2, result.toolCallsExecuted());
        }
    }

    @Nested
    @DisplayName("Max Turns")
    class MaxTurns {

        @Test
        @DisplayName("max turns reached -> MAX_TURNS_REACHED")
        void maxTurnsReached() {
            registerEchoTool("file_read");
            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/tmp/x\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));

            var loop = new ReActAgentLoop(router, registry, executionService, 3,
                    Duration.ofMinutes(5), List.of());
            var result = loop.run(AgentRequest.of("Read"));

            assertEquals(AgentResponse.FinishStatus.MAX_TURNS_REACHED, result.finishStatus());
            assertEquals(3, result.turnsExecuted());
        }

        @Test
        @DisplayName("request.maxTurns overrides global default")
        void requestMaxTurnsOverride() {
            registerEchoTool("file_read");
            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/tmp/x\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));

            var result = agentLoop.run(
                    new AgentRequest("Read", null, null, null, 2, null, null, null));

            assertEquals(AgentResponse.FinishStatus.MAX_TURNS_REACHED, result.finishStatus());
            assertEquals(2, result.turnsExecuted());
        }
    }

    @Nested
    @DisplayName("Timeout")
    class Timeout {

        @Test
        @DisplayName("run timeout -> TIMEOUT")
        void timedOut() {
            registerEchoTool("file_read");
            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/tmp/x\"}");
            // Enqueue many responses so the loop keeps running until timeout triggers
            for (int i = 0; i < 100; i++) {
                chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            }

            var loop = new ReActAgentLoop(router, registry, executionService, 100,
                    Duration.ofMillis(1), List.of());
            var result = loop.run(AgentRequest.of("Read"));

            assertEquals(AgentResponse.FinishStatus.TIMEOUT, result.finishStatus());
            assertNotNull(result.traceId());
        }
    }

    @Nested
    @DisplayName("Interrupt & Resume")
    class InterruptResume {

        @Test
        @DisplayName("interrupt() -> INTERRUPTED")
        void interrupted() throws Exception {
            registerEchoTool("file_read");
            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/tmp/x\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));

            var blockLatch = new CountDownLatch(1);
            chatModel.blockNextCall(blockLatch);

            var traceId = "test-interrupt-001";
            var latch = new CountDownLatch(1);
            var ref = new AtomicReference<AgentResponse>();

            var thread = new Thread(() -> {
                ref.set(agentLoop.run(new AgentRequest(
                        "Read", null, null, null, null, null, traceId, null)));
                latch.countDown();
            });
            thread.start();

            Thread.sleep(200);
            assertTrue(agentLoop.isRunning(traceId));
            agentLoop.interrupt(traceId);
            blockLatch.countDown();

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            var result = ref.get();
            assertEquals(AgentResponse.FinishStatus.INTERRUPTED, result.finishStatus());
            assertEquals(traceId, result.traceId());
            assertTrue(result.turnsExecuted() >= 1);
            thread.join();
        }

        @Test
        @DisplayName("interrupt unknown traceId -> no exception")
        void interruptUnknownTraceId() {
            assertDoesNotThrow(() -> agentLoop.interrupt("nonexistent"));
        }

        @Test
        @DisplayName("isRunning returns true during execution")
        void isRunning() throws Exception {
            registerEchoTool("file_read");
            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/tmp/x\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(textResponse("done", new TokenUsage(5, 5)));

            var blockLatch = new CountDownLatch(1);
            chatModel.blockNextCall(blockLatch);

            var traceId = "test-running-001";
            var latch = new CountDownLatch(1);
            var runningObserved = new java.util.concurrent.atomic.AtomicBoolean(false);

            var thread = new Thread(() -> {
                agentLoop.run(new AgentRequest("Read", null, null, null, null, null, traceId, null));
                latch.countDown();
            });
            thread.start();

            Thread.sleep(200);
            runningObserved.set(agentLoop.isRunning(traceId));

            agentLoop.interrupt(traceId);
            blockLatch.countDown();
            assertTrue(latch.await(10, TimeUnit.SECONDS));

            assertTrue(runningObserved.get());
            assertFalse(agentLoop.isRunning(traceId));
            thread.join();
        }

        @Test
        @DisplayName("resume from prior history")
        void resumeFromHistory() {
            chatModel.enqueue(textResponse("continued", new TokenUsage(5, 5)));

            var priorHistory = List.of(
                    Message.user("What is 1+1?"),
                    Message.assistant("2")
            );

            var result = agentLoop.run(AgentRequest.resume("Continue", priorHistory));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals("continued", result.content());
            assertEquals(4, result.history().size());
            assertEquals("What is 1+1?", result.history().get(0).content());
            assertEquals("Continue", result.history().get(2).content());
        }
    }

    @Nested
    @DisplayName("Trace ID")
    class TraceId {

        @Test
        @DisplayName("auto-generated when not provided")
        void autoGenerated() {
            chatModel.enqueue(textResponse("ok", new TokenUsage(1, 1)));

            var result = agentLoop.run(AgentRequest.of("Hi"));

            assertNotNull(result.traceId());
            assertFalse(result.traceId().isBlank());
        }

        @Test
        @DisplayName("provided traceId passed through")
        void providedTraceId() {
            chatModel.enqueue(textResponse("ok", new TokenUsage(1, 1)));

            var result = agentLoop.run(new AgentRequest(
                    "Hi", null, null, null, null, null, "my-trace-123", null));

            assertEquals("my-trace-123", result.traceId());
        }
    }

    @Nested
    @DisplayName("Tool Error Feedback")
    class ToolErrorFeedback {

        @Test
        @DisplayName("tool execution failure -> error fed back to conversation")
        void toolFailureFedBack() {
            registry.register(
                    new ToolDefinition("file_read", "read", Map.of()),
                    call -> ToolResult.failure(call.id(), "disk error"));

            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(textResponse("ok", new TokenUsage(5, 5)));

            var result = agentLoop.run(AgentRequest.of("Read"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            var toolMsg = result.history().stream()
                    .filter(Message::isTool)
                    .findFirst()
                    .orElse(null);
            assertNotNull(toolMsg);
            assertTrue(toolMsg.content().startsWith("Error:"));
            assertTrue(toolMsg.content().contains("disk error"));
        }

        @Test
        @DisplayName("permission denied -> denial fed back to conversation")
        void permissionDeniedFedBack() {
            var permissionManager = new DefaultPermissionManager();
            ApprovalGateway autoApprove = request -> ApprovalResponse.approved("auto");
            var interceptor = new SecurityInterceptor(
                    permissionManager, autoApprove, new Slf4jAuditLogger());
            var deniedService = new ToolExecutionService(registry, interceptor);

            registry.register(
                    new ToolDefinition("file_read", "read", Map.of()),
                    call -> ToolResult.success(call.id(), "content"));

            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/workspace/data.txt\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(textResponse("ok", new TokenUsage(5, 5)));

            var loop = new ReActAgentLoop(router, registry, deniedService, 10,
                    Duration.ofMinutes(5), List.of());
            var result = loop.run(AgentRequest.of("Read"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            var toolMsg = result.history().stream()
                    .filter(Message::isTool)
                    .findFirst()
                    .orElse(null);
            assertNotNull(toolMsg);
            assertTrue(toolMsg.content().startsWith("Error:"));
        }

        @Test
        @DisplayName("empty tool name -> skipped with error message")
        void emptyToolName() {
            var toolCall = new ToolCall("call_1", "", "{}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.enqueue(textResponse("ok", new TokenUsage(5, 5)));

            var result = agentLoop.run(AgentRequest.of("Do something"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            var toolMsg = result.history().stream()
                    .filter(Message::isTool)
                    .findFirst()
                    .orElse(null);
            assertNotNull(toolMsg);
            assertTrue(toolMsg.content().contains("empty tool name"));
        }
    }

    @Nested
    @DisplayName("Stop Conditions")
    class StopConditions {

        @Test
        @DisplayName("custom StopCondition met -> STOP_CONDITION")
        void stopConditionMet() {
            chatModel.enqueue(textResponse("answer", new TokenUsage(5, 5)));

            StopCondition alwaysStop = (session, response) -> true;
            var loop = new ReActAgentLoop(router, registry, executionService, 10,
                    Duration.ofMinutes(5), List.of(alwaysStop));

            var result = loop.run(AgentRequest.of("Hi"));

            assertEquals(AgentResponse.FinishStatus.STOP_CONDITION, result.finishStatus());
            assertEquals("answer", result.content());
            assertEquals(1, result.turnsExecuted());
        }

        @Test
        @DisplayName("StopCondition not met -> normal COMPLETED")
        void stopConditionNotMet() {
            chatModel.enqueue(textResponse("answer", new TokenUsage(5, 5)));

            StopCondition neverStop = (session, response) -> false;
            var loop = new ReActAgentLoop(router, registry, executionService, 10,
                    Duration.ofMinutes(5), List.of(neverStop));

            var result = loop.run(AgentRequest.of("Hi"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
        }

        @Test
        @DisplayName("token budget StopCondition -> stops when budget exceeded")
        void tokenBudgetStopCondition() {
            registerEchoTool("file_read");
            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/tmp/x\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(100, 50), FinishReason.TOOL_CALLS));
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(100, 50), FinishReason.TOOL_CALLS));
            chatModel.enqueue(textResponse("done", new TokenUsage(10, 5)));

            StopCondition tokenBudget = (session, response) ->
                    session.totalUsage().totalTokens() > 200;
            var loop = new ReActAgentLoop(router, registry, executionService, 10,
                    Duration.ofMinutes(5), List.of(tokenBudget));

            var result = loop.run(AgentRequest.of("Read"));

            assertEquals(AgentResponse.FinishStatus.STOP_CONDITION, result.finishStatus());
            assertTrue(result.totalUsage().totalTokens() > 200);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("LLM error on first turn -> ERROR, turnsExecuted=0")
        void firstTurnError() {
            chatModel.setError(new RuntimeException("LLM unavailable"));

            var result = agentLoop.run(AgentRequest.of("Hi"));

            assertEquals(AgentResponse.FinishStatus.ERROR, result.finishStatus());
            assertTrue(result.error().contains("LLM unavailable"));
            assertEquals(0, result.turnsExecuted());
            assertNotNull(result.traceId());
        }

        @Test
        @DisplayName("LLM error on second turn -> ERROR, turnsExecuted=1")
        void secondTurnError() {
            registerEchoTool("file_read");
            var toolCall = new ToolCall("call_1", "file_read", "{\"path\":\"/tmp/x\"}");
            chatModel.enqueue(toolCallResponse(toolCall, new TokenUsage(10, 5)));
            chatModel.setError(new RuntimeException("LLM down"));

            var result = agentLoop.run(AgentRequest.of("Read"));

            assertEquals(AgentResponse.FinishStatus.ERROR, result.finishStatus());
            assertEquals(1, result.turnsExecuted());
            assertTrue(result.error().contains("LLM down"));
        }
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("maxTurns <= 0 -> exception")
        void invalidMaxTurns() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReActAgentLoop(router, registry, executionService, 0,
                            Duration.ofMinutes(5), List.of()));
        }

        @Test
        @DisplayName("null args -> exception")
        void nullArgs() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReActAgentLoop(null, registry, executionService, 10,
                            Duration.ofMinutes(5), List.of()));
        }

        @Test
        @DisplayName("invalid timeout -> exception")
        void invalidTimeout() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ReActAgentLoop(router, registry, executionService, 10,
                            Duration.ZERO, List.of()));
        }
    }

    @Nested
    @DisplayName("Request Validation")
    class RequestValidation {

        @Test
        @DisplayName("blank userPrompt -> exception")
        void blankUserPrompt() {
            assertThrows(IllegalArgumentException.class, () -> AgentRequest.of(""));
        }

        @Test
        @DisplayName("maxTurns <= 0 -> exception")
        void invalidMaxTurns() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AgentRequest("Hi", null, null, null, 0, null, null, null));
        }
    }

    // ==================== Helpers ====================

    private void registerEchoTool(String name) {
        registry.register(
                new ToolDefinition(name, "echo tool", Map.of()),
                call -> ToolResult.success(call.id(), "echo: " + call.arguments()));
    }

    private static ChatResponse textResponse(String content, TokenUsage usage) {
        return ChatResponse.of(content, List.of(), usage, FinishReason.STOP);
    }

    private static ChatResponse toolCallResponse(ToolCall toolCall, TokenUsage usage) {
        return ChatResponse.of("", List.of(toolCall), usage, FinishReason.TOOL_CALLS);
    }

    private static ChatResponse toolCallResponse(ToolCall toolCall, TokenUsage usage, FinishReason reason) {
        return ChatResponse.of("", List.of(toolCall), usage, reason);
    }

    private static ChatResponse toolCallResponse(List<ToolCall> toolCalls, TokenUsage usage) {
        return ChatResponse.of("", toolCalls, usage, FinishReason.TOOL_CALLS);
    }

    private static class StubChatModel implements ChatModel {
        private final Queue<ChatResponse> responses = new LinkedList<>();
        private RuntimeException error;
        private CountDownLatch blockLatch;

        void enqueue(ChatResponse response) {
            responses.add(response);
        }

        void setError(RuntimeException error) {
            this.error = error;
        }

        void blockNextCall(CountDownLatch latch) {
            this.blockLatch = latch;
        }

        @Override
        public ChatResponse call(ChatRequest request) {
            if (blockLatch != null) {
                try {
                    blockLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                blockLatch = null;
            }
            if (!responses.isEmpty()) {
                return responses.poll();
            }
            if (error != null) {
                throw error;
            }
            throw new IllegalStateException("No more stub responses");
        }
    }
}