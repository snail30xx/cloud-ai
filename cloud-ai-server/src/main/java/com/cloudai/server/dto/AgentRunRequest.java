package com.cloudai.server.dto;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Agent 运行请求 DTO。
 *
 * @param prompt    用户输入（必填）
 * @param provider  LLM provider 名称，null 时用默认
 * @param maxTurns  最大轮次，null 时用默认
 * @param timeout   超时，null 时用默认
 * @param traceId   追踪 ID，null 时自动生成
 * @author cloud-ai
 * @since 1.0
 */
public record AgentRunRequest(
        String prompt,
        @Nullable String provider,
        @Nullable Integer maxTurns,
        @Nullable Duration timeout,
        @Nullable String traceId) {

    public AgentRunRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (maxTurns != null && maxTurns <= 0) {
            throw new IllegalArgumentException("maxTurns must be positive");
        }
    }
}
