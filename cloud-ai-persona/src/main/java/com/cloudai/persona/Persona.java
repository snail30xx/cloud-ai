package com.cloudai.persona;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 人格定义 — 描述 Agent 的角色、行为准则、语气和约束。
 *
 * <p>人格层通过 {@link com.cloudai.persona.PersonaAssembler} 组装为
 * 最终的 system prompt，注入到 LLM 调用上下文。</p>
 *
 * @param id           人格标识
 * @param name         显示名称
 * @param role         角色描述（如 "software engineer"）
 * @param systemPrompt 基础系统提示词
 * @param guidelines   行为准则列表
 * @param toneStyle    语气风格描述
 * @param constraints  约束列表（禁止行为）
 * @author cloud-ai
 * @since 1.0
 */
public record Persona(
        String id,
        String name,
        @Nullable String role,
        String systemPrompt,
        List<String> guidelines,
        @Nullable String toneStyle,
        List<String> constraints) {

    public Persona {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt must not be blank");
        }
        guidelines = guidelines != null ? List.copyOf(guidelines) : List.of();
        constraints = constraints != null ? List.copyOf(constraints) : List.of();
    }

    /** 创建最简人格（仅 id、name 和 systemPrompt）。 */
    public static Persona of(String id, String name, String systemPrompt) {
        return new Persona(id, name, null, systemPrompt, List.of(), null, List.of());
    }
}
