package com.cloudai.skills.spi;

import com.cloudai.skills.model.Skill;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 技能注册表 SPI — 管理技能的发现、注册和查找。
 *
 * <p>实现负责扫描工作目录下的 {@code .agents/skills/} 和 {@code .claude/skills/}
 * 目录，解析每个 SKILL.md 的 frontmatter 摘要。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface SkillRegistry {

    /**
     * 列出所有已注册技能的摘要。
     *
     * @return 技能列表
     */
    List<Skill> list();

    /**
     * 按名称查找技能摘要。
     *
     * @param name 技能名
     * @return 技能摘要，不存在返回 null
     */
    @Nullable Skill find(String name);

    /**
     * 注册技能。
     *
     * @param skill 技能摘要
     */
    void register(Skill skill);
}
