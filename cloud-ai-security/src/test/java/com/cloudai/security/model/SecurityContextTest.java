package com.cloudai.security.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityContext")
class SecurityContextTest {

    @Test
    @DisplayName("should create with all fields")
    void shouldCreateWithAllFields() {
        var ctx = new SecurityContext("agent1", "user1", "session1", Map.of("tenant", "t1"));
        assertEquals("agent1", ctx.agentId());
        assertEquals("user1", ctx.userId());
        assertEquals("session1", ctx.sessionId());
        assertEquals("t1", ctx.metadata().get("tenant"));
    }

    @Test
    @DisplayName("should default metadata to empty map")
    void shouldDefaultMetadata() {
        var ctx = new SecurityContext("agent1", null, null, null);
        assertTrue(ctx.metadata().isEmpty());
    }

    @Test
    @DisplayName("of() should create minimal context")
    void of() {
        var ctx = SecurityContext.of("agent1");
        assertEquals("agent1", ctx.agentId());
        assertNull(ctx.userId());
        assertNull(ctx.sessionId());
    }
}