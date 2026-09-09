package com.cloudai.skills.model;

import java.nio.file.Path;
import java.util.List;

/**
 * 技能定义 — 一个 SKILL.md 文件的元数据摘要。
 *
 * <p>技能采用渐进加载：注册时只保留 frontmatter 摘要（name、description、triggers），
 * 需要使用时才通过 {@code load_skill} 工具加载完整内容。</p>
 *
 * @param name        技能名（唯一标识）
 * @param description 简短描述
 * @param triggers    触发关键词列表
 * @param skillFile   SKILL.md 文件路径
 * @author cloud-ai
 * @since 1.0
 */
public record Skill(
        String name,
        String description,
        List<String> triggers,
        Path skillFile) {

    public Skill {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("skill name must not be blank");
        }
        triggers = triggers != null ? List.copyOf(triggers) : List.of();
    }
}
