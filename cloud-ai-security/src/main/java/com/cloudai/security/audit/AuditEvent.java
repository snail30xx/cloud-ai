package com.cloudai.security.audit;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * 审计事件。
 *
 * @param agentId   Agent 标识
 * @param operation 操作类型
 * @param target    操作目标
 * @param result    操作结果
 * @param timestamp 时间戳
 * @param metadata  扩展元数据
 * @author cloud-ai
 * @since 1.0
 */
public record AuditEvent(
        @Nullable String agentId,
        String operation,
        @Nullable String target,
        String result,
        Instant timestamp,
       @Nullable Map<String, Object> metadata) {

    /** 审计结果常量，用于 {@link com.cloudai.security.audit.Slf4jAuditLogger} 分级判断。 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_ALLOWED = "ALLOWED";
    public static final String RESULT_DENIED = "DENIED";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_ERROR = "ERROR";

    public AuditEvent {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("result must not be blank");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /** 创建审计事件（使用当前时间）。 */
    public static AuditEvent of(String agentId, String operation, String target, String result) {
        return new AuditEvent(agentId, operation, target, result, Instant.now(), Map.of());
    }

    /** 创建审计事件（带元数据）。 */
    public static AuditEvent of(String agentId, String operation, String target, String result,
                                Map<String, Object> metadata) {
        return new AuditEvent(agentId, operation, target, result, Instant.now(), metadata);
    }
}
