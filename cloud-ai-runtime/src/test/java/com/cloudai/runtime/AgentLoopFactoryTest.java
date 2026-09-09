package com.cloudai.runtime;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.ChatResponse;
import com.cloudai.core.spi.ChatModel;
import com.cloudai.execution.impl.DefaultToolRegistry;
import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.impl.PlanThenExecuteAgentLoop;
import com.cloudai.runtime.impl.ReActAgentLoop;
import com.cloudai.runtime.model.AgentType;
import com.cloudai.runtime.spi.AgentLoop;
import com.cloudai.runtime.spi.StopCondition;
import com.cloudai.security.impl.DefaultPermissionManager;
import com.cloudai.security.impl.SecurityInterceptor;
import com.cloudai.security.impl.Slf4jAuditLogger;
import com.cloudai.security.model.ApprovalResponse;
import com.cloudai.security.spi.ApprovalGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentLoopFactory")
class AgentLoopFactoryTest {

    private ModelRouter router;
    private ToolRegistry registry;
    private ToolExecutionService executionService;

    @BeforeEach
    void setUp() {
        router = new ModelRouter("stub");
        router.register("stub", new StubChatModel());

        registry = new DefaultToolRegistry();

        var permissionManager = new DefaultPermissionManager();
        ApprovalGateway autoApprove = request -> ApprovalResponse.approved("auto");
        var interceptor = new SecurityInterceptor(
                permissionManager, autoApprove, new Slf4jAuditLogger());
        executionService = new ToolExecutionService(registry, interceptor);
    }

    @Nested
    @DisplayName("Static factory methods")
    class StaticFactory {

        @Test
        @DisplayName("reAct() -> ReActAgentLoop")
        void reAct() {
            var loop = AgentLoopFactory.reAct(router, registry, executionService,
                    10, Duration.ofMinutes(5), List.of());

            assertInstanceOf(ReActAgentLoop.class, loop);
        }

        @Test
        @DisplayName("planThenExecute() -> PlanThenExecuteAgentLoop")
        void planThenExecute() {
            var loop = AgentLoopFactory.planThenExecute(router, registry, executionService,
                    10, Duration.ofMinutes(5), List.of());

            assertInstanceOf(PlanThenExecuteAgentLoop.class, loop);
        }

        @Test
        @DisplayName("planThenExecute(maxPlanSteps) -> PlanThenExecuteAgentLoop with custom maxPlanSteps")
        void planThenExecuteWithMaxPlanSteps() {
            var loop = AgentLoopFactory.planThenExecute(router, registry, executionService,
                    10, Duration.ofMinutes(5), List.of(), 5);

            assertInstanceOf(PlanThenExecuteAgentLoop.class, loop);
        }
    }

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("default type=REACT")
        void defaultType() {
            var loop = AgentLoopFactory.builder(router, registry, executionService).build();

            assertInstanceOf(ReActAgentLoop.class, loop);
        }

        @Test
        @DisplayName("type(PLAN_THEN_EXECUTE) -> PlanThenExecuteAgentLoop")
        void planThenExecuteType() {
            var loop = AgentLoopFactory.builder(router, registry, executionService)
                    .type(AgentType.PLAN_THEN_EXECUTE)
                    .build();

            assertInstanceOf(PlanThenExecuteAgentLoop.class, loop);
        }

        @Test
        @DisplayName("type(REACT) -> ReActAgentLoop")
        void reactType() {
            var loop = AgentLoopFactory.builder(router, registry, executionService)
                    .type(AgentType.REACT)
                    .build();

            assertInstanceOf(ReActAgentLoop.class, loop);
        }

        @Test
        @DisplayName("custom maxTurns, timeout, maxPlanSteps")
        void customConfig() {
            var loop = AgentLoopFactory.builder(router, registry, executionService)
                    .type(AgentType.PLAN_THEN_EXECUTE)
                    .maxTurns(100)
                    .timeout(Duration.ofMinutes(30))
                    .maxPlanSteps(5)
                    .stopConditions(List.of((s, r) -> false))
                    .build();

            assertInstanceOf(PlanThenExecuteAgentLoop.class, loop);
        }

        @Test
        @DisplayName("null modelRouter -> exception")
        void nullRouter() {
            assertThrows(IllegalArgumentException.class,
                    () -> AgentLoopFactory.builder(null, registry, executionService));
        }

        @Test
        @DisplayName("invalid maxTurns -> exception")
        void invalidMaxTurns() {
            assertThrows(IllegalArgumentException.class,
                    () -> AgentLoopFactory.builder(router, registry, executionService)
                            .maxTurns(0));
        }

        @Test
        @DisplayName("invalid maxPlanSteps -> exception")
        void invalidMaxPlanSteps() {
            assertThrows(IllegalArgumentException.class,
                    () -> AgentLoopFactory.builder(router, registry, executionService)
                            .maxPlanSteps(0));
        }

        @Test
        @DisplayName("null type -> exception")
        void nullType() {
            assertThrows(IllegalArgumentException.class,
                    () -> AgentLoopFactory.builder(router, registry, executionService)
                            .type(null));
        }
    }

    private static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(ChatRequest request) {
            return ChatResponse.of("stub", java.util.List.of(), null, null);
        }
    }
}