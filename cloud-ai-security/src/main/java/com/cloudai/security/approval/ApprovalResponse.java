package com.cloudai.security.approval;

import org.jspecify.annotations.Nullable;

/**
 * 审批响应。
 *
 * @param approved 是否批准
 * @param reason   批准/拒绝原因
 * @param approver 审批人标识（null 表示自动审批或超时）
 * @author cloud-ai
 * @since 1.0
 */
public record ApprovalResponse(
        boolean approved,
        String reason,
        @Nullable String approver) {

    public ApprovalResponse {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    /** 自动批准。 */
    public static ApprovalResponse approved(String reason) {
        return new ApprovalResponse(true, reason, null);
    }

    /** 人工批准。 */
    public static ApprovalResponse approved(String reason, String approver) {
        return new ApprovalResponse(true, reason, approver);
    }

    /** 拒绝。 */
    public static ApprovalResponse denied(String reason) {
        return new ApprovalResponse(false, reason, null);
    }

    /** 超时拒绝。 */
    public static ApprovalResponse timeout() {
        return new ApprovalResponse(false, "Approval timeout", null);
    }
}