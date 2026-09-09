package com.cloudai.execution.config;

import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.security.config.SecurityAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExecutionAutoConfiguration")
class ExecutionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityAutoConfiguration.class, ExecutionAutoConfiguration.class);

    @Nested
    @DisplayName("default behavior")
    class DefaultBehavior {
        @Test
        @DisplayName("should create ToolRegistry and ToolExecutionService")
        void shouldCreateBeans() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(ToolRegistry.class);
                assertThat(context).hasSingleBean(ToolExecutionService.class);
            });
        }

        @Test
        @DisplayName("should register file_read, file_write, and shell_exec tools")
        void shouldRegisterDefaultTools() {
            contextRunner.run(context -> {
                var registry = context.getBean(ToolRegistry.class);
                var defs = registry.listDefinitions();
                assertThat(defs).hasSize(3);
                assertThat(defs).extracting("name")
                        .containsExactlyInAnyOrder("file_read", "file_write", "shell_exec");
            });
        }
    }

    @Nested
    @DisplayName("module disabled")
    class ModuleDisabled {
        @Test
        @DisplayName("should not create beans when enabled=false")
        void shouldNotCreateBeansWhenDisabled() {
            contextRunner.withPropertyValues("cloud-ai.execution.enabled=false").run(context -> {
                assertThat(context).doesNotHaveBean(ToolRegistry.class);
                assertThat(context).doesNotHaveBean(ToolExecutionService.class);
            });
        }
    }

    @Nested
    @DisplayName("tool toggles")
    class ToolToggles {
        @Test
        @DisplayName("should not register shell_exec when shellEnabled=false")
        void shouldNotRegisterShellWhenDisabled() {
            contextRunner.withPropertyValues(
                    "cloud-ai.execution.shell-enabled=false").run(context -> {
                var registry = context.getBean(ToolRegistry.class);
                var defs = registry.listDefinitions();
                assertThat(defs).hasSize(2);
                assertThat(defs).extracting("name")
                        .containsExactlyInAnyOrder("file_read", "file_write");
            });
        }

        @Test
        @DisplayName("should not register file_write when fileWriteEnabled=false")
        void shouldNotRegisterWriteWhenDisabled() {
            contextRunner.withPropertyValues(
                    "cloud-ai.execution.file-write-enabled=false").run(context -> {
                var registry = context.getBean(ToolRegistry.class);
                var defs = registry.listDefinitions();
                assertThat(defs).hasSize(2);
                assertThat(defs).extracting("name")
                        .containsExactlyInAnyOrder("file_read", "shell_exec");
            });
        }
    }
}
