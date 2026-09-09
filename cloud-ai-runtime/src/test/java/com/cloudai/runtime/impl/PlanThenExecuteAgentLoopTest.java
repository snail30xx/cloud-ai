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
import com.cloudai.security.model.ApprovalResponse;
import com.cloudai.security.spi.ApprovalGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PlanThenExecuteAgentLoop")
class PlanThenExecuteAgentLoopTest {

    private StubChatModel chatModel;
    private ModelRouter router;
    private DefaultToolRegistry registry;
    private ToolExecutionService executionService;
    private PlanThenExecuteAgentLoop agentLoop;

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

        ApprovalGateway autoApprove = request -> ApprovalResponse.approved("auto");
        var interceptor = new SecurityInterceptor(
                permissionManager, autoApprove, new Slf4jAuditLogger());
        executionService = new ToolExecutionService(registry, interceptor);

        agentLoop = new PlanThenExecuteAgentLoop(router, registry, executionService,
                10, Duration.ofMinutes(5), List.of(), 3);
    }

    @Nested
    @DisplayName("Planning Prompt")
    class PlanningPrompt {

        @Test
        @DisplayName("no systemPrompt -> injected planning prompt")
        void injectedWhenAbsent() {
            chatModel.enqueue(textResponse("done", new TokenUsage(5, 5)));

            var result = agentLoop.run(AgentRequest.of("What files exist?"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            // First message should be system prompt (injected)
            assertEquals("system", result.history().get(0).role());
            assertTrue(result.history().get(0).content().contains("planning agent"));
        }

        @Test
        @DisplayName("explicit systemPrompt -> not overridden")
        void notOverriddenWhenProvided() {
            chatModel.enqueue(textResponse("done", new TokenUsage(5, 5)));

            var result = agentLoop.run(AgentRequest.of("Hi", "Custom prompt"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals("Custom prompt", result.history().get(0).content());
        }
    }

    @Nested
    @DisplayName("Plan Execution")
    class PlanExecution {

        @Test
        @DisplayName("plan with 3 tool calls -> all executed -> COMPLETED in 2 turns")
        void planExecuted() {
            registerEchoTool("file_read");
            var call1 = new ToolCall("c1", "file_read", "{\"path\":\"/a\"}");
            var call2 = new ToolCall("c2", "file_read", "{\"path\":\"/b\"}");
            var call3 = new ToolCall("c3", "file_read", "{\"path\":\"/c\"}");
            chatModel.enqueue(toolCallResponse(List.of(call1, call2, call3), new TokenUsage(20, 10)));
            chatModel.enqueue(textResponse("Found 3 files", new TokenUsage(30, 10)));

            var result = agentLoop.run(AgentRequest.of("List files"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals("Found 3 files", result.content());
            assertEquals(2, result.turnsExecuted());
            assertEquals(3, result.toolCallsExecuted());
        }

        @Test
        @DisplayName("plan exceeds maxPlanSteps -> truncated")
        void planTruncated() {
            registerEchoTool("file_read");
            // Plan with 5 tool calls, maxPlanSteps=3
            var calls = new ArrayList<ToolCall>();
            for (int i = 0; i < 5; i++) {
                calls.add(new ToolCall("c" + i, "file_read", "{\"path\":\"/" + i + "\"}"));
            }
            chatModel.enqueue(toolCallResponse(calls, new TokenUsage(20, 10)));
            chatModel.enqueue(textResponse("done", new TokenUsage(5, 5)));

            var result = agentLoop.run(AgentRequest.of("Read many"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            // Only 3 tool calls executed (truncated from 5)
            assertEquals(3, result.toolCallsExecuted());
            // 2 turns: planning + synthesis
            assertEquals(2, result.turnsExecuted());
        }

        @Test
        @DisplayName("LLM answers directly (no plan) -> COMPLETED in 1 turn")
        void noPlanDirectAnswer() {
            chatModel.enqueue(textResponse("I can answer directly", new TokenUsage(10, 5)));

            var result = agentLoop.run(AgentRequest.of("What is 1+1?"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals("I can answer directly", result.content());
            assertEquals(1, result.turnsExecuted());
            assertEquals(0, result.toolCallsExecuted());
        }

        @Test
        @DisplayName("synthesis turn returns tool calls -> executed again")
        void synthesisReturnsToolCalls() {
            registerEchoTool("file_read");
            var call1 = new ToolCall("c1", "file_read", "{\"path\":\"/a\"}");
            // Turn 1 (PLANNING): plan with 1 tool call
            chatModel.enqueue(toolCallResponse(List.of(call1), new TokenUsage(10, 5)));
            // Turn 2 (SYNTHESIS): returns another tool call (not truncated, not planning)
            var call2 = new ToolCall("c2", "file_read", "{\"path\":\"/b\"}");
            chatModel.enqueue(toolCallResponse(List.of(call2), new TokenUsage(10, 5)));
            // Turn 3: final answer
            chatModel.enqueue(textResponse("Final answer", new TokenUsage(5, 5)));

            var result = agentLoop.run(AgentRequest.of("Read files"));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, result.finishStatus());
            assertEquals("Final answer", result.content());
            assertEquals(3, result.turnsExecuted());
            assertEquals(2, result.toolCallsExecuted());
        }
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("maxPlanSteps <= 0 -> exception")
        void invalidMaxPlanSteps() {
            assertThrows(IllegalArgumentException.class,
                    () -> new PlanThenExecuteAgentLoop(router, registry, executionService,
                            10, Duration.ofMinutes(5), List.of(), 0));
        }

        @Test
        @DisplayName("default maxPlanSteps = 10")
        void defaultMaxPlanSteps() {
            registerEchoTool("file_read");
            var calls = new ArrayList<ToolCall>();
            for (int i = 0; i < 8; i++) {
                calls.add(new ToolCall("c" + i, "file_read", "{\"path\":\"/" + i + "\"}"));
            }
            chatModel.enqueue(toolCallResponse(calls, new TokenUsage(50, 20)));
            chatModel.enqueue(textResponse("done", new TokenUsage(5, 5)));

            var loop = new PlanThenExecuteAgentLoop(router, registry, executionService,
                    10, Duration.ofMinutes(5), List.of());
            var result = loop.run(AgentRequest.of("Read"));

            // 8 < 10, no truncation
            assertEquals(8, result.toolCallsExecuted());
        }
    }

    // ==================== Helpers ====================

    private void registerEchoTool(String name) {
        registry.register(
                new ToolDefinition(name, "echo", Map.of()),
                call -> ToolResult.success(call.id(), "echo: " + call.arguments()));
    }

    private static ChatResponse textResponse(String content, TokenUsage usage) {
        return ChatResponse.of(content, List.of(), usage, FinishReason.STOP);
    }

    private static ChatResponse toolCallResponse(List<ToolCall> toolCalls, TokenUsage usage) {
        return ChatResponse.of("", toolCalls, usage, FinishReason.TOOL_CALLS);
    }

    private static class StubChatModel implements ChatModel {
        private final Queue<ChatResponse> responses = new LinkedList<>();

        void enqueue(ChatResponse response) {
            responses.add(response);
        }

        @Override
        public ChatResponse call(ChatRequest request) {
            if (responses.isEmpty()) {
                throw new IllegalStateException("No more stub responses");
            }
            return responses.poll();
        }
    }
}