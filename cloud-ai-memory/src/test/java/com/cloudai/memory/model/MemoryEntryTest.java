package com.cloudai.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryEntry")
class MemoryEntryTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {
        @Test
        @DisplayName("of() should create entry with default importance 0.5")
        void shouldCreateWithDefaultImportance() {
            var entry = MemoryEntry.of("agent1", "hello world", MemoryType.SEMANTIC);
            assertNotNull(entry.id());
            assertEquals("agent1", entry.agentId());
            assertEquals("hello world", entry.content());
            assertEquals(MemoryType.SEMANTIC, entry.type());
            assertEquals(0.5, entry.importance(), 0.001);
            assertNotNull(entry.timestamp());
            assertTrue(entry.metadata().isEmpty());
        }

        @Test
        @DisplayName("working() should create WORKING type with sessionId")
        void shouldCreateWorkingMemory() {
            var entry = MemoryEntry.working("agent1", "session1", "current task");
            assertEquals(MemoryType.WORKING, entry.type());
            assertEquals("session1", entry.sessionId());
        }

        @Test
        @DisplayName("semantic() should create SEMANTIC type without sessionId")
        void shouldCreateSemanticMemory() {
            var entry = MemoryEntry.semantic("agent1", "user likes coffee", 0.8);
            assertEquals(MemoryType.SEMANTIC, entry.type());
            assertNull(entry.sessionId());
            assertEquals(0.8, entry.importance(), 0.001);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {
        @Test
        @DisplayName("should reject blank agentId")
        void shouldRejectBlankAgentId() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MemoryEntry(null, "", null, "content", MemoryType.WORKING, 0.5, null, null));
        }

        @Test
        @DisplayName("should reject blank content")
        void shouldRejectBlankContent() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MemoryEntry(null, "agent1", null, "", MemoryType.WORKING, 0.5, null, null));
        }

        @Test
        @DisplayName("should reject null type")
        void shouldRejectNullType() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MemoryEntry(null, "agent1", null, "content", null, 0.5, null, null));
        }

        @Test
        @DisplayName("should reject importance out of [0.0, 1.0]")
        void shouldRejectOutOfRangeImportance() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MemoryEntry(null, "agent1", null, "content", MemoryType.WORKING, -0.1, null, null));
            assertThrows(IllegalArgumentException.class, () ->
                    new MemoryEntry(null, "agent1", null, "content", MemoryType.WORKING, 1.1, null, null));
        }

        @Test
        @DisplayName("should accept importance boundaries 0.0 and 1.0")
        void shouldAcceptImportanceBoundaries() {
            var e1 = new MemoryEntry(null, "agent1", null, "content", MemoryType.WORKING, 0.0, null, null);
            var e2 = new MemoryEntry(null, "agent1", null, "content", MemoryType.WORKING, 1.0, null, null);
            assertEquals(0.0, e1.importance(), 0.001);
            assertEquals(1.0, e2.importance(), 0.001);
        }
    }

    @Nested
    @DisplayName("auto-generation")
    class AutoGeneration {
        @Test
        @DisplayName("should auto-generate id when null")
        void shouldAutoGenerateId() {
            var entry = new MemoryEntry(null, "agent1", null, "content", MemoryType.WORKING, 0.5, null, null);
            assertNotNull(entry.id());
            assertFalse(entry.id().isBlank());
        }

        @Test
        @DisplayName("should use current time when timestamp null")
        void shouldUseCurrentTimeWhenNull() {
            var before = Instant.now();
            var entry = new MemoryEntry(null, "agent1", null, "content", MemoryType.WORKING, 0.5, null, null);
            var after = Instant.now();
            assertNotNull(entry.timestamp());
            assertTrue(!entry.timestamp().isBefore(before) && !entry.timestamp().isAfter(after));
        }

        @Test
        @DisplayName("should copy metadata map")
        void shouldCopyMetadata() {
            var entry = new MemoryEntry(null, "agent1", null, "content", MemoryType.WORKING, 0.5, null,
                    Map.of("key", "value"));
            assertEquals("value", entry.metadata().get("key"));
        }
    }
}

