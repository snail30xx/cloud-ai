package com.cloudai.spring.config;

import com.cloudai.skills.config.SkillProperties;
import com.cloudai.skills.SkillRegistry;
import com.cloudai.spring.properties.CloudAiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.skills.enabled", havingValue = "true", matchIfMissing = true)
public class SkillsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SkillProperties skillProperties(CloudAiProperties props) {
        var skill = props.skill() != null ? props.skill() : new CloudAiProperties.Skill();
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
}