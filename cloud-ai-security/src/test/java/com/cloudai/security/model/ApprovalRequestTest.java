package com.cloudai.security.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApprovalRequest")
class ApprovalRequestTest {

    @Nested
    @DisplayName("construction")
    class Construction {
        @Test
        @DisplayName("should create with all fields")
        void shouldCreateWithAllFields() {
            var req = new ApprovalRequest("FILE_WRITE", RiskLevel.MEDIUM,
                    java.util.Map.of("path", "/etc/hosts"), Duration.ofSeconds(10), null);
            assertEquals("FILE_WRITE", req.operation());
            assertEquals(RiskLevel.MEDIUM, req.risk());
            assertEquals(Duration.ofSeconds(10), req.timeout());
        }

        @Test
        @DisplayName("should leave timeout null when not specified")
        void shouldLeaveTimeoutNull() {
            var req = new ApprovalRequest("op", RiskLevel.LOW, null, null, null);
            assertNull(req.timeout());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {
        @Test
        @DisplayName("should reject blank operation")
        void shouldRejectBlankOperation() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ApprovalRequest("", RiskLevel.LOW, null, null, null));
        }

        @Test
        @DisplayName("should reject null risk")
        void shouldRejectNullRisk() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ApprovalRequest("op", null, null, null, null));
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {
        @Test
        @DisplayName("lowRisk() should create LOW risk request")
        void lowRisk() {
            var req = ApprovalRequest.lowRisk("FILE_READ");
            assertEquals(RiskLevel.LOW, req.risk());
        }

        @Test
        @DisplayName("of() should create request with specified risk")
        void of() {
            var req = ApprovalRequest.of("SHELL_EXEC", RiskLevel.HIGH);
            assertEquals(RiskLevel.HIGH, req.risk());
        }
    }
}
