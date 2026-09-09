package com.cloudai.memory.impl;

import com.cloudai.core.model.Message;
import com.cloudai.memory.spi.ContextManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimpleContextManager")
class SimpleContextManagerTest {

    private ContextManager manager;

    @BeforeEach
    void setUp() {
        manager = new SimpleContextManager();
    }

    @Nested
    @DisplayName("estimateTokens")
    class EstimateTokens {
        @Test
        @DisplayName("should return 0 for empty list")
        void shouldReturnZeroForEmpty() {
            assertEquals(0, manager.estimateTokens(List.of()));
        }

        @Test
        @DisplayName("should estimate tokens for messages")
        void shouldEstimateTokens() {
            var messages = List.of(Message.system("hello world"), Message.user("hi"));
            var tokens = manager.estimateTokens(messages);
            // "hello world" = 11 chars / 4 + 4 = 6; "hi" = 2/4 + 4 = 4; total = 10
            assertEquals(10, tokens);
        }
    }

    @Nested
    @DisplayName("compress")
    class Compress {
        @Test
        @DisplayName("should return all messages when within budget")
        void shouldReturnAllWhenWithinBudget() {
            var messages = List.of(Message.system("sys"), Message.user("hello"));
            var result = manager.compress(messages, 1000);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should return empty for zero budget")
        void shouldReturnEmptyForZeroBudget() {
            var messages = List.of(Message.user("hello"));
            var result = manager.compress(messages, 0);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for null input")
        void shouldReturnEmptyForNullInput() {
            assertTrue(manager.compress(null, 1000).isEmpty());
        }

        @Test
        @DisplayName("should keep system messages and truncate old conversation")
        void shouldKeepSystemAndTruncateOld() {
            var messages = new ArrayList<Message>();
            messages.add(Message.system("system prompt that takes some tokens"));
            for (int i = 0; i < 20; i++) {
                messages.add(Message.user("message number " + i + " with enough content"));
            }
            // Budget only allows a few messages
            var result = manager.compress(messages, 50);
            // System should always be kept
            assertTrue(result.stream().anyMatch(Message::isSystem));
            // Result should be smaller than original
            assertTrue(result.size() < messages.size());
            // Most recent message should be kept
            assertTrue(result.stream().anyMatch(m -> m.content().contains("number 19")));
        }

        @Test
        @DisplayName("should keep only system when budget is tiny")
        void shouldKeepOnlySystemWhenBudgetTiny() {
            var messages = List.of(
                    Message.system("short"),
                    Message.user("a"));
            var result = manager.compress(messages, 5);
            // System takes ~4 tokens, user takes ~4 tokens, total 8 > 5
            // So only system should be kept
            assertEquals(1, result.size());
            assertTrue(result.get(0).isSystem());
        }

        @Test
        @DisplayName("should keep most recent messages when truncating")
        void shouldKeepMostRecent() {
            var messages = new ArrayList<Message>();
            for (int i = 0; i < 10; i++) {
                messages.add(Message.user("msg " + i));
            }
            // Each msg ~5 tokens, budget of 15 allows ~3 messages
            var result = manager.compress(messages, 15);
            assertTrue(result.size() <= 3);
            // Should include the last message
            assertEquals("msg 9", result.get(result.size() - 1).content());
        }
    }
}
