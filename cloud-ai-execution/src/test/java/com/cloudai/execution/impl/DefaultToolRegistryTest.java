package com.cloudai.execution.impl;

import com.cloudai.core.model.ToolDefinition;
import com.cloudai.core.model.ToolCall;
import com.cloudai.execution.model.ToolResult;
import com.cloudai.execution.spi.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultToolRegistry")
class DefaultToolRegistryTest {

    private DefaultToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
    }

    @Test
    @DisplayName("find should return null for unregistered tool")
    void findUnregistered() {
        assertNull(registry.find("nonexistent"));
    }

    @Test
    @DisplayName("register and find should work")
    void registerAndFind() {
        var def = new ToolDefinition("test_tool", "A test tool", java.util.Map.of());
        ToolExecutor exec = call -> ToolResult.success(call.id(), "ok");
        registry.register(def, exec);

        var found = registry.find("test_tool");
        assertNotNull(found);
        assertEquals("ok", found.execute(new ToolCall("1", "test_tool", "")).output());
    }

    @Test
    @DisplayName("listDefinitions should return all registered tools")
    void listDefinitions() {
        registry.register(new ToolDefinition("tool_a", "A", java.util.Map.of()),
                call -> ToolResult.success(call.id(), "a"));
        registry.register(new ToolDefinition("tool_b", "B", java.util.Map.of()),
                call -> ToolResult.success(call.id(), "b"));

        var defs = registry.listDefinitions();
        assertEquals(2, defs.size());
    }

    @Test
    @DisplayName("register should overwrite existing tool with same name")
    void overwriteExisting() {
        registry.register(new ToolDefinition("tool", "v1", java.util.Map.of()),
                call -> ToolResult.success(call.id(), "v1"));
        registry.register(new ToolDefinition("tool", "v2", java.util.Map.of()),
                call -> ToolResult.success(call.id(), "v2"));

        var result = registry.find("tool").execute(new ToolCall("1", "tool", ""));
        assertEquals("v2", result.output());
    }
}
