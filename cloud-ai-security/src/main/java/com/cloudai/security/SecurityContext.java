package com.cloudai.security;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 安全上下文 — 携带调用方身份和会话信息。
 *
 * @param agentId   Agent 标识
 * @param userId    用户标识
 * @param sessionId 会话标识
 * @param metadata  扩展元数据
 * @author cloud-ai
 * @since 1.0
 */
public record SecurityContext(
        @Nullable String agentId,
        @Nullable String userId,
        @Nullable String sessionId,
        @Nullable Map<String, Object> metadata) {

    public SecurityContext {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /** 创建最小上下文（仅 agentId）。 */
    public static SecurityContext of(String agentId) {
        return new SecurityContext(agentId, null, null, Map.of());
    }
}