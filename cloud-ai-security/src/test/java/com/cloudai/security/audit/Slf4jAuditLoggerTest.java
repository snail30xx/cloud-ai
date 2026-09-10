package com.cloudai.security.audit;

import com.cloudai.security.audit.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Slf4jAuditLogger")
class Slf4jAuditLoggerTest {

    private Slf4jAuditLogger logger;

    @BeforeEach
    void setUp() {
        logger = new Slf4jAuditLogger();
    }

    @Nested
    @DisplayName("logAccess")
    class LogAccess {
        @Test
        @DisplayName("should log access event without throwing")
        void shouldLogAccess() {
            var event = AuditEvent.of("agent1", "FILE_READ", "/data.txt", "ACCESSED");
            assertDoesNotThrow(() -> logger.logAccess(event));
        }
    }

    @Nested
    @DisplayName("logDecision")
    class LogDecision {
        @Test
        @DisplayName("should log allowed decision without throwing")
        void shouldLogAllowedDecision() {
            var event = AuditEvent.of("agent1", "FILE_READ", "/data.txt", "ALLOWED");
            assertDoesNotThrow(() -> logger.logDecision(event));
        }

        @Test
        @DisplayName("should log denied decision without throwing")
        void shouldLogDeniedDecision() {
            var event = AuditEvent.of("agent1", "FILE_WRITE", "/etc/hosts", "DENIED");
            assertDoesNotThrow(() -> logger.logDecision(event));
        }
    }

    @Nested
    @DisplayName("logExecution")
    class LogExecution {
        @Test
        @DisplayName("should log success execution without throwing")
        void shouldLogSuccessExecution() {
            var event = AuditEvent.of("agent1", "FILE_READ", "/data.txt", "SUCCESS");
            assertDoesNotThrow(() -> logger.logExecution(event));
        }

        @Test
        @DisplayName("should log failed execution without throwing")
        void shouldLogFailedExecution() {
            var event = AuditEvent.of("agent1", "SHELL_EXEC", "rm -rf /", "FAILED");
            assertDoesNotThrow(() -> logger.logExecution(event));
        }

        @Test
        @DisplayName("should log error execution without throwing")
        void shouldLogErrorExecution() {
            var event = AuditEvent.of("agent1", "NETWORK_CALL", "https://api.example.com",
                    "ERROR: timeout");
            assertDoesNotThrow(() -> logger.logExecution(event));
        }
    }

    @Nested
    @DisplayName("with metadata")
    class WithMetadata {
        @Test
        @DisplayName("should handle events with metadata")
        void shouldHandleMetadata() {
            var event = AuditEvent.of("agent1", "SHELL_EXEC", "ls -la", "SUCCESS",
                    Map.of("duration_ms", 150L, "exit_code", 0));
            assertDoesNotThrow(() -> logger.logExecution(event));
        }
    }
}