package com.cloudai.security.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApprovalResponse")
class ApprovalResponseTest {

    @Nested
    @DisplayName("approved()")
    class Approved {
        @Test
        @DisplayName("auto-approved without approver")
        void autoApproved() {
            var resp = ApprovalResponse.approved("Auto-approved: low risk");
            assertTrue(resp.approved());
            assertNull(resp.approver());
        }

        @Test
        @DisplayName("manual approved with approver")
        void manualApproved() {
            var resp = ApprovalResponse.approved("Looks safe", "admin");
            assertTrue(resp.approved());
            assertEquals("admin", resp.approver());
        }
    }

    @Nested
    @DisplayName("denied()")
    class Denied {
        @Test
        @DisplayName("should create denied response")
        void denied() {
            var resp = ApprovalResponse.denied("Too risky");
            assertFalse(resp.approved());
            assertNull(resp.approver());
        }
    }

    @Nested
    @DisplayName("timeout()")
    class Timeout {
        @Test
        @DisplayName("should create timeout response")
        void timeout() {
            var resp = ApprovalResponse.timeout();
            assertFalse(resp.approved());
            assertEquals("Approval timeout", resp.reason());
        }
    }
}