package com.cloudai.skills.config;

import com.cloudai.skills.impl.FilesystemSkillRegistry;
import com.cloudai.skills.impl.LoadSkillExecutor;
import com.cloudai.skills.impl.SkillMenuPromptSection;
import com.cloudai.skills.spi.SkillLoader;
import com.cloudai.skills.spi.SkillRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 技能层自动装配。
 *
 * @author cloud-ai
 * @since 1.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cloud-ai.skills", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SkillProperties.class)
public class SkillAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SkillRegistry skillRegistry(SkillProperties props) {
        return new FilesystemSkillRegistry(props.getWorkDir());
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillLoader skillLoader(SkillRegistry registry) {
        return (SkillLoader) registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public SkillMenuPromptSection skillMenuPromptSection(SkillRegistry registry) {
        return new SkillMenuPromptSection(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoadSkillExecutor loadSkillExecutor(SkillLoader loader) {
        return new LoadSkillExecutor(loader);
    }
}
