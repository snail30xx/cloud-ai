package com.cloudai.llm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProviderProperties 校验单元测试。
 */
class LlmPropertiesTest {

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("should validate valid provider config")
        void shouldValidateValidProviderConfig() {
            var props = new ProviderProperties(
                    "https://api.openai.com/v1", "key", "model",
                    Duration.ofSeconds(30), 128_000, null, List.of()
            );
            assertDoesNotThrow(props::validate);
        }

        @Test
        @DisplayName("should throw on invalid baseUrl protocol")
        void shouldThrowOnInvalidBaseUrlProtocol() {
            var props = new ProviderProperties(
                    "http://api.openai.com/v1", "key", "model",
                    Duration.ofSeconds(30), 128_000, null, List.of()
            );
            var ex = assertThrows(IllegalStateException.class, props::validate);
            assertTrue(ex.getMessage().contains("https://"));
        }
    }
}