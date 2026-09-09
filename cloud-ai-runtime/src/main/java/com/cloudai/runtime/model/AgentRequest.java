package com.cloudai.runtime.model;

import com.cloudai.core.model.Message;
import com.cloudai.core.model.ModelOptions;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * Agent 调用请求。
 *
 * @param userPrompt   用户输入
 * @param systemPrompt 系统提示词，null 时不注入 system message（仅在无 history 时生效）
 * @param provider     LLM provider 名称，null 时使用默认 provider
 * @param options      模型选项，null 时使用 provider 默认
 * @param maxTurns     最大 LLM 调用轮次，null 时使用全局默认值
 * @param timeout      单次运行超时，null 时使用全局默认值
 * @param traceId      全链路追踪 ID，null 时自动生成
 * @param history      预设对话历史（用于 resume），null 时从空白开始
 * @author cloud-ai
 * @since 1.0
 */
public record AgentRequest(
        String userPrompt,
        @Nullable String systemPrompt,
        @Nullable String provider,
        @Nullable ModelOptions options,
        @Nullable Integer maxTurns,
        @Nullable Duration timeout,
        @Nullable String traceId,
        @Nullable List<Message> history) {

    public AgentRequest {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt must not be blank");
        }
        if (maxTurns != null && maxTurns <= 0) {
            throw new IllegalArgumentException("maxTurns must be positive");
        }
        if (timeout != null && timeout.isNegative() || timeout != null && timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        history = history != null ? List.copyOf(history) : null;
    }

    /** 创建最简请求（仅用户输入）。 */
    public static AgentRequest of(String userPrompt) {
        return new AgentRequest(userPrompt, null, null, null, null, null, null, null);
    }

    /** 创建带系统提示词的请求。 */
    public static AgentRequest of(String userPrompt, String systemPrompt) {
        return new AgentRequest(userPrompt, systemPrompt, null, null, null, null, null, null);
    }

    /** 从已有对话历史恢复（resume），追加新的用户输入。 */
    public static AgentRequest resume(String userPrompt, List<Message> history) {
        return new AgentRequest(userPrompt, null, null, null, null, null, null, history);
    }
}