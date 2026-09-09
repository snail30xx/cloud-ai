package com.cloudai.security.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PermissionResult")
class PermissionResultTest {

    @Nested
    @DisplayName("allow()")
    class Allow {
        @Test
        @DisplayName("should create allowed result with default reason")
        void shouldCreateAllowedResult() {
            var result = PermissionResult.allow();
            assertTrue(result.allowed());
            assertEquals("Allowed", result.reason());
        }

        @Test
        @DisplayName("should create allowed result with custom reason")
        void shouldCreateAllowedWithCustomReason() {
            var result = PermissionResult.allow("Matched whitelist rule");
            assertTrue(result.allowed());
            assertEquals("Matched whitelist rule", result.reason());
        }
    }

    @Nested
    @DisplayName("deny()")
    class Deny {
        @Test
        @DisplayName("should create denied result with reason")
        void shouldCreateDeniedResult() {
            var result = PermissionResult.deny("No matching rule");
            assertFalse(result.allowed());
            assertEquals("No matching rule", result.reason());
        }
    }

    @Test
    @DisplayName("should reject blank reason")
    void shouldRejectBlankReason() {
        assertThrows(IllegalArgumentException.class, () -> new PermissionResult(true, ""));
        assertThrows(IllegalArgumentException.class, () -> new PermissionResult(true, "  "));
    }
}