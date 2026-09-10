package com.cloudai.spring.config;

import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.skills.LoadSkillExecutor;
import com.cloudai.skills.config.SkillProperties;
import com.cloudai.skills.SkillRegistry;
import com.cloudai.skills.SkillLoader;
import com.cloudai.spring.properties.CloudAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.skills.enabled", havingValue = "true", matchIfMissing = true)
public class SkillsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkillsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SkillProperties skillProperties(CloudAiProperties props) {
        var skill = props.skills() != null ? props.skills() : new CloudAiProperties.Skill();
        var p = new SkillProperties();
        if (skill.workDir() != null) {
            p.setWorkDir(skill.workDir());
        }
        p.setEnabled(skill.enabled());
        return p;
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry(SkillProperties props) {
        return com.cloudai.skills.config.SkillAutoConfiguration.skillRegistry(props);
    }

    /**
     * 创建 load_skill 执行器并注册进 ToolRegistry（对齐 CloudAi facade 的装配行为），
     * 使 LLM 能按需渐进加载技能全文。工作目录下无技能时不注册。
     */
    @Bean
    @ConditionalOnMissingBean
    public LoadSkillExecutor loadSkillExecutor(SkillRegistry skillRegistry, ToolRegistry toolRegistry) {
        var loader = (SkillLoader) skillRegistry;
        var executor = com.cloudai.skills.config.SkillAutoConfiguration.loadSkillExecutor(loader);
        if (!skillRegistry.list().isEmpty()) {
            toolRegistry.register(LoadSkillExecutor.definition(), executor);
            log.info("Registered tool: load_skill ({} skill(s) available)", skillRegistry.list().size());
        } else {
            log.info("No skills found under workDir, load_skill tool not registered");
        }
        return executor;
    }
}