package com.cloudai.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * LLM 配置属性 — 绑定 {@code cloud-ai.llm} 命名空间。
 *
 * @author cloud-ai
 * @since 1.0
 */
@ConfigurationProperties("cloud-ai.llm")
public record LlmProperties(
        @Nullable String defaultProvider,
        @Nullable Map<String, ProviderProperties> providers) {

    public LlmProperties {
        providers = providers != null ? Map.copyOf(providers) : Map.of();
    }

    /** 获取默认 provider 配置 */
    public ProviderProperties defaultProviderProperties() {
        var props = providers.get(defaultProvider);
        if (props == null) {
            throw new IllegalStateException(
                    "Default provider '" + defaultProvider + "' not found in configured providers: " + providers.keySet());
        }
        return props;
    }
}
