package com.cloudai.llm.config;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * 单个 LLM Provider 配置。
 *
 * @param baseUrl         API 基础地址
 * @param apiKey          API 密钥
 * @param model           默认模型名称
 * @param timeout         请求超时
 * @param maxContextTokens 最大上下文 token 数
 * @param maxRetries     最大重试次数（默认 3）
 * @param capabilities    模型能力列表
 * @author cloud-ai
 * @since 1.0
 */
public record ProviderProperties(
        @Nullable String baseUrl,
        @Nullable String apiKey,
        @Nullable String model,
        @Nullable Duration timeout,
        @Nullable Integer maxContextTokens,
        @Nullable Integer maxRetries,
        @Nullable List<String> capabilities) {

    public ProviderProperties {
        capabilities = capabilities != null ? List.copyOf(capabilities) : List.of("chat", "tool_calling");
        if (maxContextTokens == null || maxContextTokens <= 0) {
            maxContextTokens = 128_000;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(60);
        }
        if (maxRetries == null || maxRetries < 0) {
            maxRetries = 3;
        }
    }

    /** 校验配置是否有效 */
    public void validate() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("LLM provider base-url must not be empty");
        }
        if (!baseUrl.startsWith("https://")) {
            throw new IllegalStateException("LLM provider base-url must start with https://, got: " + baseUrl);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM provider api-key must not be empty. "
                    + "Set the environment variable or configure a mock mode.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("LLM provider model must not be empty");
        }
    }
}