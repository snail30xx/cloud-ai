package com.cloudai.skills.impl;

import com.cloudai.core.spi.PromptSection;
import com.cloudai.skills.model.Skill;
import com.cloudai.skills.spi.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 技能菜单段落 — 列出所有可用技能的名称和描述。
 *
 * <p>order=50，动态段落。LLM 看到菜单后可通过 {@code load_skill} 工具
 * 加载需要使用的技能完整内容。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class SkillMenuPromptSection {

    private static final Logger log = LoggerFactory.getLogger(SkillMenuPromptSection.class);

    private final SkillRegistry registry;

    public SkillMenuPromptSection(SkillRegistry registry) {
        this.registry = registry;
    }

    public PromptSection build() {
        var skills = registry.list();
        if (skills.isEmpty()) {
            return PromptSectionImpl.empty("SkillMenu");
        }
        var sb = new StringBuilder("[Available Skills]\n");
        sb.append("Use the load_skill tool to load full instructions when needed.\n\n");
        for (var skill : skills) {
            sb.append("- ").append(skill.name());
            if (!skill.description().isBlank()) {
                sb.append(": ").append(skill.description());
            }
            sb.append("\n");
        }
        return new PromptSection() {
            @Override public String name() { return "SkillMenu"; }
            @Override public String content() { return sb.toString().strip(); }
            @Override public int order() { return 50; }
        };
    }
}
