package com.cloudai.memory;

import com.cloudai.memory.MemoryEntry;
import com.cloudai.memory.MemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryMemoryStore")
class InMemoryMemoryStoreTest {

    private InMemoryMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryMemoryStore();
    }

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {
        @Test
        @DisplayName("should save and retrieve by id")
        void shouldSaveAndRetrieveById() {
            var entry = MemoryEntry.of("agent1", "hello", MemoryType.SEMANTIC);
            store.save(entry);
            var found = store.findById(entry.id());
            assertTrue(found.isPresent());
            assertEquals("hello", found.get().content());
        }

        @Test
        @DisplayName("should return empty for non-existent id")
        void shouldReturnEmptyForNonExistentId() {
            assertTrue(store.findById("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("should reject null entry")
        void shouldRejectNullEntry() {
            assertThrows(IllegalArgumentException.class, () -> store.save(null));
        }
    }

    @Nested
    @DisplayName("findByAgent")
    class FindByAgent {
        @Test
        @DisplayName("should find all memories for an agent")
        void shouldFindAllForAgent() {
            store.save(MemoryEntry.of("agent1", "memory A", MemoryType.SEMANTIC));
            store.save(MemoryEntry.of("agent1", "memory B", MemoryType.EPISODIC));
            store.save(MemoryEntry.of("agent2", "memory C", MemoryType.SEMANTIC));
            var results = store.findByAgent("agent1");
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("should return empty for unknown agent")
        void shouldReturnEmptyForUnknownAgent() {
            assertTrue(store.findByAgent("unknown").isEmpty());
        }

        @Test
        @DisplayName("should return results sorted by timestamp descending")
        void shouldSortByTimestampDescending() throws Exception {
            var early = new MemoryEntry("id1", "agent1", null, "early", MemoryType.SEMANTIC, 0.5,
                    java.time.Instant.parse("2024-01-01T00:00:00Z"), null);
            Thread.sleep(10);
            var late = new MemoryEntry("id2", "agent1", null, "late", MemoryType.SEMANTIC, 0.5,
                    java.time.Instant.parse("2024-12-01T00:00:00Z"), null);
            store.save(early);
            store.save(late);
            var results = store.findByAgent("agent1");
            assertEquals("late", results.get(0).content());
            assertEquals("early", results.get(1).content());
        }
    }

    @Nested
    @DisplayName("findBySession")
    class FindBySession {
        @Test
        @DisplayName("should find memories by session")
        void shouldFindBySession() {
            store.save(MemoryEntry.working("agent1", "sess1", "task A"));
            store.save(MemoryEntry.working("agent1", "sess2", "task B"));
            store.save(MemoryEntry.working("agent1", "sess1", "task C"));
            var results = store.findBySession("agent1", "sess1");
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("should return empty for blank inputs")
        void shouldReturnEmptyForBlankInputs() {
            assertTrue(store.findBySession("", "sess1").isEmpty());
            assertTrue(store.findBySession("agent1", "").isEmpty());
        }
    }

    @Nested
    @DisplayName("delete operations")
    class DeleteOperations {
        @Test
        @DisplayName("should delete by id")
        void shouldDeleteById() {
            var entry = MemoryEntry.of("agent1", "hello", MemoryType.SEMANTIC);
            store.save(entry);
            store.deleteById(entry.id());
            assertTrue(store.findById(entry.id()).isEmpty());
        }

        @Test
        @DisplayName("should delete by session")
        void shouldDeleteBySession() {
            store.save(MemoryEntry.working("agent1", "sess1", "task A"));
            store.save(MemoryEntry.working("agent1", "sess1", "task B"));
            store.save(MemoryEntry.working("agent1", "sess2", "task C"));
            store.deleteBySession("agent1", "sess1");
            assertTrue(store.findBySession("agent1", "sess1").isEmpty());
            assertEquals(1, store.findBySession("agent1", "sess2").size());
        }
    }
}
