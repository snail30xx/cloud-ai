package com.cloudai.runtime.config;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.ChatResponse;
import com.cloudai.core.spi.ChatModel;
import com.cloudai.execution.impl.DefaultToolRegistry;
import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.impl.ReActAgentLoop;
import com.cloudai.runtime.spi.AgentLoop;
import com.cloudai.runtime.spi.StopCondition;
import com.cloudai.security.impl.DefaultPermissionManager;
import com.cloudai.security.impl.SecurityInterceptor;
import com.cloudai.security.impl.Slf4jAuditLogger;
import com.cloudai.security.spi.ApprovalGateway;
import com.cloudai.security.model.ApprovalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RuntimeAutoConfiguration")
class RuntimeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuntimeAutoConfiguration.class))
            .withBean(ModelRouter.class, RuntimeAutoConfigurationTest::stubRouter)
            .withBean(ToolRegistry.class, DefaultToolRegistry::new)
            .withBean(ToolExecutionService.class, RuntimeAutoConfigurationTest::stubExecutionService);

    @Test
    @DisplayName("默认启用 → 创建 AgentLoop + RuntimeProperties")
    void shouldCreateAgentLoopByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentLoop.class);
            assertThat(context.getBean(AgentLoop.class)).isInstanceOf(ReActAgentLoop.class);
            assertThat(context).hasSingleBean(RuntimeProperties.class);
        });
    }

    @Test
    @DisplayName("cloud-ai.runtime.enabled=false → 不创建 AgentLoop")
    void shouldNotCreateWhenDisabled() {
        contextRunner.withPropertyValues("cloud-ai.runtime.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(AgentLoop.class);
        });
    }

    @Test
    @DisplayName("默认 maxTurns=50, timeout=10m")
    void shouldUseDefaultProperties() {
        contextRunner.run(context -> {
            var props = context.getBean(RuntimeProperties.class);
            assertThat(props.maxTurns()).isEqualTo(50);
            assertThat(props.timeout()).isEqualTo(Duration.ofMinutes(10));
        });
    }

    @Test
    @DisplayName("自定义 AgentLoop → 默认实现不创建")
    void shouldAllowReplacingReActAgentLoop() {
        AgentLoop custom = request -> null;
        contextRunner.withBean(AgentLoop.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(AgentLoop.class);
            assertThat(context.getBean(AgentLoop.class)).isSameAs(custom);
        });
    }

    @Test
    @DisplayName("注入 StopCondition Bean → 传递给 ReActAgentLoop")
    void shouldInjectStopConditions() {
        StopCondition sc = (session, response) -> false;
        contextRunner.withBean(StopCondition.class, () -> sc).run(context -> {
            assertThat(context).hasSingleBean(StopCondition.class);
            assertThat(context).hasSingleBean(AgentLoop.class);
        });
    }

    // ==================== Helpers ====================

    private static ModelRouter stubRouter() {
        var router = new ModelRouter("stub");
        router.register("stub", new StubChatModel());
        return router;
    }

    private static ToolExecutionService stubExecutionService() {
        var registry = new DefaultToolRegistry();
        var permissionManager = new DefaultPermissionManager();
        var interceptor = new SecurityInterceptor(
                permissionManager,
                request -> ApprovalResponse.approved("auto"),
                new Slf4jAuditLogger());
        return new ToolExecutionService(registry, interceptor);
    }

    private static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(ChatRequest request) {
            return ChatResponse.of("stub", java.util.List.of(), null, null);
        }
    }
}