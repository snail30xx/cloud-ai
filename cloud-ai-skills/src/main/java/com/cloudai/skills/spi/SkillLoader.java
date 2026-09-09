package com.cloudai.skills.spi;

import org.jspecify.annotations.Nullable;

/**
 * 技能加载器 SPI — 按需加载技能的完整内容（SKILL.md body）。
 *
 * <p>渐进加载设计：注册时只保留摘要，使用时才加载完整文件内容。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@FunctionalInterface
public interface SkillLoader {

    /**
     * 加载技能的完整内容。
     *
     * @param skillName 技能名
     * @return SKILL.md body 文本（不含 frontmatter），不存在返回 null
     */
    @Nullable String load(String skillName);
}
