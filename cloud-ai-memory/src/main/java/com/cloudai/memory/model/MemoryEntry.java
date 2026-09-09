package com.cloudai.memory.model;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 记忆条目 — 单条记忆的不可变表示。
 *
 * @param id         唯一标识，null 时自动生成 UUID
 * @param agentId    Agent 标识
 * @param sessionId  会话标识，跨会话记忆为 null
 * @param content    记忆内容
 * @param type       记忆类型
 * @param importance 重要性权重 [0.0, 1.0]，越高越优先检索
 * @param timestamp  创建时间，null 时使用当前时间
 * @param metadata   扩展元数据
 * @author cloud-ai
 * @since 1.0
 */
public record MemoryEntry(
        @Nullable String id,
        String agentId,
        @Nullable String sessionId,
        String content,
        MemoryType type,
        double importance,
        @Nullable Instant timestamp,
        @Nullable Map<String, Object> metadata) {

    public MemoryEntry {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (importance < 0.0 || importance > 1.0) {
            throw new IllegalArgumentException("importance must be in [0.0, 1.0]");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /** 创建最小记忆条目（agentId + content + type，默认重要性 0.5）。 */
    public static MemoryEntry of(String agentId, String content, MemoryType type) {
        return new MemoryEntry(null, agentId, null, content, type, 0.5, null, null);
    }

    /** 创建带会话和工作记忆的条目。 */
    public static MemoryEntry working(String agentId, String sessionId, String content) {
        return new MemoryEntry(null, agentId, sessionId, content, MemoryType.WORKING, 0.5, null, null);
    }

    /** 创建跨会话的语义记忆。 */
    public static MemoryEntry semantic(String agentId, String content, double importance) {
        return new MemoryEntry(null, agentId, null, content, MemoryType.SEMANTIC, importance, null, null);
    }
}
