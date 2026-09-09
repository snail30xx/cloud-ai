package com.cloudai.memory.advisor;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.Message;
import com.cloudai.memory.impl.InMemoryMemoryStore;
import com.cloudai.memory.impl.KeywordMemoryRetriever;
import com.cloudai.memory.model.MemoryEntry;
import com.cloudai.memory.model.MemoryType;
import com.cloudai.memory.spi.MemoryRetriever;
import com.cloudai.memory.spi.MemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryAdvisor")
class MemoryAdvisorTest {

    private MemoryStore store;
    private MemoryRetriever retriever;
    private MemoryAdvisor advisor;

    @BeforeEach
    void setUp() {
        store = new InMemoryMemoryStore();
        retriever = new KeywordMemoryRetriever(store);
        advisor = new MemoryAdvisor(retriever, "agent1", 5);
    }

    @Nested
    @DisplayName("memory injection")
    class MemoryInjection {
        @Test
        @DisplayName("should inject memories as system message before user message")
        void shouldInjectMemories() {
            store.save(MemoryEntry.semantic("agent1", "user prefers Python over Java", 0.9));
            var request = new ChatRequest(
                    List.of(Message.user("tell me about the user Python preferences")),
                    null, null);
            var result = advisor.advise(request, req -> req);
            // First message should be the injected memory system message
            assertTrue(result.messages().get(0).isSystem());
            assertTrue(result.messages().get(0).content().contains("Relevant Memories"));
            assertTrue(result.messages().get(0).content().contains("Python"));
        }

        @Test
        @DisplayName("should pass through when no memories found")
        void shouldPassThroughWhenNoMemories() {
            var request = new ChatRequest(
                    List.of(Message.user("hello")),
                    null, null);
            var result = advisor.advise(request, req -> req);
            assertEquals(1, result.messages().size());
            assertTrue(result.messages().get(0).isUser());
        }

        @Test
        @DisplayName("should pass through when no user message")
        void shouldPassThroughWhenNoUserMessage() {
            store.save(MemoryEntry.semantic("agent1", "some memory", 0.9));
            var request = new ChatRequest(
                    List.of(Message.system("system prompt")),
                    null, null);
            var result = advisor.advise(request, req -> req);
            assertEquals(1, result.messages().size());
        }

        @Test
        @DisplayName("should use last user message as query")
        void shouldUseLastUserMessageAsQuery() {
            store.save(MemoryEntry.semantic("agent1", "coffee preference", 0.9));
            var request = new ChatRequest(
                    List.of(Message.user("hello"), Message.assistant("hi"), Message.user("tell me about coffee")),
                    null, null);
            var result = advisor.advise(request, req -> req);
            assertTrue(result.messages().get(0).content().contains("coffee"));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {
        @Test
        @DisplayName("should reject null retriever")
        void shouldRejectNullRetriever() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MemoryAdvisor(null, "agent1", 5));
        }

        @Test
        @DisplayName("should reject blank agentId")
        void shouldRejectBlankAgentId() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MemoryAdvisor(retriever, "", 5));
        }

        @Test
        @DisplayName("should reject non-positive maxResults")
        void shouldRejectNonPositiveMaxResults() {
            assertThrows(IllegalArgumentException.class, () ->
                    new MemoryAdvisor(retriever, "agent1", 0));
        }
    }
}

