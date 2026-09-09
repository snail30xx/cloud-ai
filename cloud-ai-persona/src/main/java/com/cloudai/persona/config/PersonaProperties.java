package com.cloudai.persona.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 人格模块配置属性 — 绑定 {@code cloud-ai.persona} 命名空间。
 *
 * @param defaultPersonaId 默认人格 ID
 * @param personas         人格配置映射（key 为人格 ID）
 * @author cloud-ai
 * @since 1.0
 */
@ConfigurationProperties("cloud-ai.persona")
public record PersonaProperties(
        @Nullable String defaultPersonaId,
        @Nullable Map<String, PersonaConfig> personas) {

    public PersonaProperties {
        if (defaultPersonaId == null || defaultPersonaId.isBlank()) {
            defaultPersonaId = "default";
        }
        personas = personas != null ? Map.copyOf(personas) : Map.of();
    }

    /** 单个人格的配置（不含 id，id 由 map key 提供）。 */
    public record PersonaConfig(
            @Nullable String name,
            @Nullable String role,
            @Nullable String systemPrompt,
            @Nullable List<String> guidelines,
            @Nullable String toneStyle,
            @Nullable List<String> constraints) {

        public PersonaConfig {
            guidelines = guidelines != null ? List.copyOf(guidelines) : List.of();
            constraints = constraints != null ? List.copyOf(constraints) : List.of();
        }
    }
}
