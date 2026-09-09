package com.cloudai.security.model;

/**
 * 权限决策结果。
 *
 * @param allowed 是否允许
 * @param reason  决策原因（允许或拒绝的理由）
 * @author cloud-ai
 * @since 1.0
 */
public record PermissionResult(boolean allowed, String reason) {

    public PermissionResult {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    /** 允许操作。 */
    public static PermissionResult allow() {
        return new PermissionResult(true, "Allowed");
    }

    /** 允许操作（带原因）。 */
    public static PermissionResult allow(String reason) {
        return new PermissionResult(true, reason);
    }

    /** 拒绝操作。 */
    public static PermissionResult deny(String reason) {
        return new PermissionResult(false, reason);
    }
}