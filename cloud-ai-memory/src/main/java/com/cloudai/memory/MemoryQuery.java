package com.cloudai.memory;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 记忆检索查询。
 *
 * @param agentId    Agent 标识
 * @param queryText  检索文本（用户输入或上下文关键词）
 * @param maxResults 最大返回条数
 * @param sessionId  会话标识，null 时不按会话过滤
 * @param typeFilter 类型过滤，null 或空时返回所有类型
 * @author cloud-ai
 * @since 1.0
 */
public record MemoryQuery(
        String agentId,
        String queryText,
        int maxResults,
        @Nullable String sessionId,
        @Nullable List<MemoryType> typeFilter) {

    public MemoryQuery {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be blank");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        typeFilter = typeFilter != null ? List.copyOf(typeFilter) : null;
    }

    /** 创建最简查询（默认返回 5 条）。 */
    public static MemoryQuery of(String agentId, String queryText) {
        return new MemoryQuery(agentId, queryText, 5, null, null);
    }
}
