package com.cloudai.llm.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmException 体系单元测试。
 */
class LlmExceptionTest {

    @Nested
    @DisplayName("Retryable status")
    class RetryableTests {

        @ParameterizedTest
        @MethodSource("retryableCases")
        @DisplayName("should correctly report retryable status")
        void shouldReportRetryable(LlmException ex, boolean expectedRetryable, int expectedHttpStatus) {
            assertEquals(expectedRetryable, ex.isRetryable());
            assertEquals(expectedHttpStatus, ex.getHttpStatus());
            assertEquals("openai", ex.getProvider());
        }

        static Stream<Arguments> retryableCases() {
            return Stream.of(
                    Arguments.of(new LlmAuthException("openai", 401, "Unauthorized"), false, 401),
                    Arguments.of(new LlmRateLimitException("openai", "Too many requests"), true, 429),
                    Arguments.of(new LlmServerException("openai", 500, "Internal server error"), true, 500),
                    Arguments.of(new LlmClientException("openai", 400, "Bad request"), false, 400),
                    Arguments.of(new LlmTimeoutException("openai", "Connection timed out",
                            new java.net.SocketTimeoutException("timeout")), true, 0)
            );
        }
    }

    @Nested
    @DisplayName("Exception cause propagation")
    class CausePropagationTests {

        @Test
        @DisplayName("should propagate cause")
        void shouldPropagateCause() {
            var cause = new RuntimeException("root cause");
            var ex = new LlmTimeoutException("openai", "timeout", cause);

            assertSame(cause, ex.getCause());
        }
    }
}