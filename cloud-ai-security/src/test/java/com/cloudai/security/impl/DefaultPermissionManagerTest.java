package com.cloudai.security.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.security.model.OperationType;
import com.cloudai.security.model.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultPermissionManager")
class DefaultPermissionManagerTest {

    private DefaultPermissionManager manager;
    private SecurityContext ctx;

    @BeforeEach
    void setUp() {
        manager = new DefaultPermissionManager();
        ctx = SecurityContext.of("agent1");
    }

    @Nested
    @DisplayName("default behavior (no rules)")
    class DefaultBehavior {
        @Test
        @DisplayName("should deny all when no rules configured")
        void shouldDenyAllByDefault() {
            var result = manager.check(toolCall("file_read", "/data.txt"), ctx);
            assertFalse(result.allowed());
            assertTrue(result.reason().contains("No permission rules"));
        }
    }

    @Nested
    @DisplayName("whitelist matching")
    class Whitelist {
        @Test
        @DisplayName("should allow when whitelist rule matches")
        void shouldAllowWhitelistMatch() {
            manager.allow(OperationType.FILE_READ, "/workspace/**");
            var result = manager.check(toolCall("file_read", "/workspace/data.txt"), ctx);
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("should allow with wildcard pattern")
        void shouldAllowWildcard() {
            manager.allow(OperationType.FILE_READ, "*");
            var result = manager.check(toolCall("file_read", "/any/path.txt"), ctx);
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("should deny when whitelist pattern does not match")
        void shouldDenyNonMatchingPattern() {
            manager.allow(OperationType.FILE_READ, "/workspace/**");
            var result = manager.check(toolCall("file_read", "/etc/hosts"), ctx);
            assertFalse(result.allowed());
        }
    }

    @Nested
    @DisplayName("blacklist priority")
    class Blacklist {
        @Test
        @DisplayName("blacklist should override whitelist")
        void blacklistOverridesWhitelist() {
            manager.allow(OperationType.FILE_READ, "/workspace/**");
            manager.deny(OperationType.FILE_READ, "/workspace/secrets/**");
            var result = manager.check(toolCall("file_read", "/workspace/secrets/key.txt"), ctx);
            assertFalse(result.allowed());
            assertTrue(result.reason().contains("Denied"), "Expected 'Denied' in reason: " + result.reason());
        }

        @Test
        @DisplayName("blacklist should not affect non-matching path")
        void blacklistDoesNotAffectOtherPaths() {
            manager.allow(OperationType.FILE_READ, "/workspace/**");
            manager.deny(OperationType.FILE_READ, "/workspace/secrets/**");
            var result = manager.check(toolCall("file_read", "/workspace/normal.txt"), ctx);
            assertTrue(result.allowed());
        }
    }

    @Nested
    @DisplayName("operation type mapping")
    class OperationMapping {
        @ParameterizedTest
        @CsvSource({
                "file_read, FILE_READ",
                "read_file, FILE_READ",
                "file_write, FILE_WRITE",
                "write_file, FILE_WRITE",
                "file_delete, FILE_DELETE",
                "shell_exec, SHELL_EXEC",
                "exec_command, SHELL_EXEC",
                "http_get, NETWORK_CALL",
                "curl_request, NETWORK_CALL",
        })
        @DisplayName("should map tool name to correct operation type")
        void shouldMapToolNameToOperationType(String toolName, String expectedOperation) {
            manager.allow(OperationType.valueOf(expectedOperation), "*");
            var result = manager.check(toolCall(toolName, "/test"), ctx);
            assertTrue(result.allowed(), "Expected " + toolName + " to be allowed as " + expectedOperation);
        }
    }

    @Nested
    @DisplayName("target extraction")
    class TargetExtraction {
        @Test
        @DisplayName("should extract path from JSON arguments")
        void shouldExtractPath() {
            manager.allow(OperationType.FILE_READ, "/data/**");
            var result = manager.check(toolCall("file_read", "{\"path\":\"/data/file.txt\"}"), ctx);
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("should extract filePath from JSON arguments")
        void shouldExtractFilePath() {
            manager.allow(OperationType.FILE_READ, "/uploads/**");
            var result = manager.check(toolCall("file_read", "{\"filePath\":\"/uploads/img.png\"}"), ctx);
            assertTrue(result.allowed());
        }

        @Test
        @DisplayName("should handle empty arguments")
        void shouldHandleEmptyArguments() {
            manager.allow(OperationType.CUSTOM, "*");
            var result = manager.check(toolCall("unknown_tool", ""), ctx);
            assertTrue(result.allowed());
        }
    }

    private static ToolCall toolCall(String name, String arguments) {
        return new ToolCall("call_1", name, arguments);
    }
}