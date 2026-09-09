package com.cloudai.memory.impl;

import com.cloudai.memory.model.MemoryEntry;
import com.cloudai.memory.model.MemoryQuery;
import com.cloudai.memory.model.MemoryType;
import com.cloudai.memory.spi.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("KeywordMemoryRetriever")
class KeywordMemoryRetrieverTest {

    private MemoryStore store;
    private KeywordMemoryRetriever retriever;

    @BeforeEach
    void setUp() {
        store = new InMemoryMemoryStore();
        retriever = new KeywordMemoryRetriever(store);
    }

    @Nested
    @DisplayName("retrieval")
    class Retrieval {
        @Test
        @DisplayName("should retrieve memories matching keywords")
        void shouldRetrieveMatchingMemories() {
            store.save(MemoryEntry.semantic("agent1", "user likes coffee and tea", 0.8));
            store.save(MemoryEntry.semantic("agent1", "user works as software engineer", 0.7));
            store.save(MemoryEntry.semantic("agent1", "weather is sunny today", 0.5));

            var results = retriever.retrieve(MemoryQuery.of("agent1", "what does the user like to drink?"));
            assertFalse(results.isEmpty());
            assertTrue(results.stream().anyMatch(m -> m.content().contains("coffee")));
        }

        @Test
        @DisplayName("should return empty when no memories match")
        void shouldReturnEmptyWhenNoMatch() {
            store.save(MemoryEntry.semantic("agent1", "completely unrelated content", 0.5));
            var results = retriever.retrieve(MemoryQuery.of("agent1", "coffee"));
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("should return empty when store is empty")
        void shouldReturnEmptyWhenStoreEmpty() {
            var results = retriever.retrieve(MemoryQuery.of("agent1", "anything"));
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("should respect maxResults limit")
        void shouldRespectMaxResults() {
            for (int i = 0; i < 10; i++) {
                store.save(MemoryEntry.semantic("agent1", "coffee preference number " + i, 0.5));
            }
            var query = new MemoryQuery("agent1", "coffee", 3, null, null);
            var results = retriever.retrieve(query);
            assertEquals(3, results.size());
        }

        @Test
        @DisplayName("should filter by session id")
        void shouldFilterBySession() {
            store.save(MemoryEntry.working("agent1", "sess1", "coffee is good"));
            store.save(MemoryEntry.working("agent1", "sess2", "coffee is great"));
            var query = new MemoryQuery("agent1", "coffee", 5, "sess1", null);
            var results = retriever.retrieve(query);
            assertEquals(1, results.size());
            assertEquals("sess1", results.get(0).sessionId());
        }

        @Test
        @DisplayName("should filter by type")
        void shouldFilterByType() {
            store.save(MemoryEntry.semantic("agent1", "coffee facts", 0.8));
            store.save(MemoryEntry.of("agent1", "coffee session note", MemoryType.WORKING));
            var query = new MemoryQuery("agent1", "coffee", 5, null, List.of(MemoryType.SEMANTIC));
            var results = retriever.retrieve(query);
            assertTrue(results.stream().allMatch(m -> m.type() == MemoryType.SEMANTIC));
        }

        @Test
        @DisplayName("should return results sorted by relevance descending")
        void shouldSortByRelevance() {
            store.save(MemoryEntry.semantic("agent1", "coffee coffee coffee", 0.1));
            store.save(MemoryEntry.semantic("agent1", "coffee", 0.9));
            var results = retriever.retrieve(MemoryQuery.of("agent1", "coffee"));
            assertFalse(results.isEmpty());
            // Higher importance + keyword match should rank first
            assertEquals(0.9, results.get(0).importance(), 0.001);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {
        @Test
        @DisplayName("should return empty for null query")
        void shouldReturnEmptyForNullQuery() {
            assertTrue(retriever.retrieve(null).isEmpty());
        }

        @Test
        @DisplayName("should return empty for query with only stop words")
        void shouldReturnEmptyForStopWordsOnly() {
            store.save(MemoryEntry.semantic("agent1", "some content here", 0.5));
            var results = retriever.retrieve(MemoryQuery.of("agent1", "the a an is"));
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("should isolate results by agentId")
        void shouldIsolateByAgentId() {
            store.save(MemoryEntry.semantic("agent1", "coffee preferences", 0.8));
            store.save(MemoryEntry.semantic("agent2", "coffee preferences", 0.8));
            var results = retriever.retrieve(MemoryQuery.of("agent1", "coffee"));
            assertTrue(results.stream().allMatch(m -> "agent1".equals(m.agentId())));
        }
    }
}


