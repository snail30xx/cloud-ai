package com.cloudai.skills.config;

import com.cloudai.skills.FilesystemSkillRegistry;
import com.cloudai.skills.LoadSkillExecutor;
import com.cloudai.skills.SkillMenuPromptSection;
import com.cloudai.skills.SkillLoader;
import com.cloudai.skills.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 技能模块工厂 — 替代 Spring 自动装配。
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class SkillAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SkillAutoConfiguration.class);

    private SkillAutoConfiguration() {}

    public static SkillRegistry skillRegistry(SkillProperties props) {
        log.info("Creating FilesystemSkillRegistry: workDir={}", props.getWorkDir());
        return new FilesystemSkillRegistry(props.getWorkDir());
    }

    public static SkillLoader skillLoader(SkillRegistry registry) {
        return (SkillLoader) registry;
    }

    public static SkillMenuPromptSection skillMenuPromptSection(SkillRegistry registry) {
        return new SkillMenuPromptSection(registry);
    }

    public static LoadSkillExecutor loadSkillExecutor(SkillLoader loader) {
        return new LoadSkillExecutor(loader);
    }
}