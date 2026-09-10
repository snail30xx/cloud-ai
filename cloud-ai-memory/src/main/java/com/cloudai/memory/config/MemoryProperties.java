package com.cloudai.memory.config;


/**
 * 记忆模块配置属性 — 绑定 {@code cloud-ai.memory} 命名空间。
 *
 * <p>模块启停由 {@link MemoryAutoConfiguration} 上的
 * {@code @ConditionalOnProperty("cloud-ai.memory.enabled")} 控制，
 * 此 record 仅承载运行参数。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public record MemoryProperties(
        int maxContextTokens,
        int maxRetrievalResults,
        String defaultAgentId) {

    public MemoryProperties {
        if (maxContextTokens <= 0) {
            maxContextTokens = 8000;
        }
        if (maxRetrievalResults <= 0) {
            maxRetrievalResults = 5;
        }
        if (defaultAgentId == null || defaultAgentId.isBlank()) {
            defaultAgentId = "cloud-ai-agent";
        }
    }
}
