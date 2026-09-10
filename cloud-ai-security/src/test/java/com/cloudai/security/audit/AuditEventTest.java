package com.cloudai.security.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditEvent")
class AuditEventTest {

    @Nested
    @DisplayName("construction")
    class Construction {
        @Test
        @DisplayName("should default timestamp to now")
        void shouldDefaultTimestamp() {
            var before = Instant.now();
            var event = new AuditEvent("agent1", "FILE_READ", "/data.txt", "SUCCESS", null, null);
            var after = Instant.now();
            assertNotNull(event.timestamp());
            assertFalse(event.timestamp().isBefore(before));
            assertFalse(event.timestamp().isAfter(after));
        }

        @Test
        @DisplayName("should reject blank operation")
        void shouldRejectBlankOperation() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AuditEvent("agent1", "", "/data.txt", "SUCCESS", null, null));
        }

        @Test
        @DisplayName("should reject blank result")
        void shouldRejectBlankResult() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AuditEvent("agent1", "FILE_READ", "/data.txt", "", null, null));
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {
        @Test
        @DisplayName("of() should create event with current timestamp")
        void ofWithoutMetadata() {
            var event = AuditEvent.of("agent1", "FILE_READ", "/data.txt", "SUCCESS");
            assertEquals("agent1", event.agentId());
            assertEquals("FILE_READ", event.operation());
            assertEquals("/data.txt", event.target());
            assertEquals("SUCCESS", event.result());
            assertNotNull(event.timestamp());
            assertTrue(event.metadata().isEmpty());
        }

        @Test
        @DisplayName("of() with metadata should include metadata")
        void ofWithMetadata() {
            var event = AuditEvent.of("agent1", "SHELL_EXEC", "ls -la", "SUCCESS",
                    java.util.Map.of("duration_ms", 150L));
            assertEquals(150L, event.metadata().get("duration_ms"));
        }
    }
}