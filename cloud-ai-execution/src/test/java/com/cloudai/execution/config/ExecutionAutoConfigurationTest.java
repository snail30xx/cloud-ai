package com.cloudai.execution.config;

import com.cloudai.execution.registry.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ExecutionAutoConfiguration 内置工具装配")
class ExecutionAutoConfigurationTest {

    private static boolean hasTool(ToolRegistry registry, String name) {
        return registry.listDefinitions().stream().anyMatch(d -> d.name().equals(name));
    }

    @Test
    @DisplayName("默认配置注册全部三个内置工具")
    void defaultPropertiesRegisterAllTools() {
        var registry = ExecutionAutoConfiguration.toolRegistry(new ExecutionProperties());

        assertEquals(3, registry.listDefinitions().size());
        assertTrue(hasTool(registry, "file_read"));
        assertTrue(hasTool(registry, "file_write"));
        assertTrue(hasTool(registry, "shell_exec"));
    }

    @Test
    @DisplayName("工具开关生效 — 关闭后不注册")
    void togglesDisableTools() {
        var registry = ExecutionAutoConfiguration.toolRegistry(
                new ExecutionProperties(true, false, false, null, null));

        assertEquals(1, registry.listDefinitions().size());
        assertTrue(hasTool(registry, "file_read"));
        assertFalse(hasTool(registry, "file_write"));
        assertFalse(hasTool(registry, "shell_exec"));
    }

    @Test
    @DisplayName("shellTimeout 缺省回退 30s，workspace 可为 null（不限制）")
    void defaultsApplied() {
        var props = new ExecutionProperties(true, true, true, null, null);

        assertEquals(Duration.ofSeconds(30), props.shellTimeout());
        assertTrue(Stream.<Object>of(props.workspace()).allMatch(java.util.Objects::isNull));
    }

    @Test
    @DisplayName("workspace 传入后保留")
    void workspaceKept() {
        var ws = Path.of("./workspace");
        var props = new ExecutionProperties(true, true, true, Duration.ofSeconds(5), ws);

        assertEquals(ws, props.workspace());
        assertEquals(Duration.ofSeconds(5), props.shellTimeout());
    }
}
