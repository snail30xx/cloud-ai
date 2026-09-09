package com.cloudai.execution.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.execution.model.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShellToolExecutor")
class ShellToolExecutorTest {

    private final ShellToolExecutor executor = new ShellToolExecutor();

    @Nested
    @DisplayName("successful execution")
    class Success {
        @Test
        @DisplayName("should execute simple echo command")
        void shouldExecuteEcho() {
            var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            var command = isWindows ? "echo hello" : "echo hello";

            var result = executor.execute(
                    new ToolCall("call_1", "shell_exec", "{\"command\":\"" + command + "\"}"));

            assertTrue(result.success());
            assertTrue(result.output().contains("hello"));
        }
    }

    @Nested
    @DisplayName("failure cases")
    class Failure {
        @Test
        @DisplayName("should fail when command parameter missing")
        void shouldFailWhenCommandMissing() {
            var result = executor.execute(
                    new ToolCall("call_1", "shell_exec", "{}"));

            assertFalse(result.success());
            assertTrue(result.error().contains("command"));
        }

        @Test
        @DisplayName("should fail with non-zero exit code")
        void shouldFailWithNonZeroExit() {
            var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            var command = isWindows ? "exit 1" : "false";

            var result = executor.execute(
                    new ToolCall("call_1", "shell_exec", "{\"command\":\"" + command + "\"}"));

            assertFalse(result.success());
        }
    }
}
