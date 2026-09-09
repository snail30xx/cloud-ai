package com.cloudai.context.config;

import com.cloudai.context.impl.DefaultEnvironmentProvider;
import com.cloudai.context.impl.FilesystemProjectContextProvider;
import com.cloudai.context.spi.ContextProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 上下文层自动装配。
 *
 * @author cloud-ai
 * @since 1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ContextProperties.class)
public class ContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "environmentProvider")
    public ContextProvider environmentProvider(ContextProperties props) {
        return new DefaultEnvironmentProvider(props.getWorkDir());
    }

    @Bean
    @ConditionalOnMissingBean(name = "projectContextProvider")
    public ContextProvider projectContextProvider(ContextProperties props) {
        if (!props.isProjectContextEnabled()) {
            return () -> com.cloudai.context.impl.PromptSectionImpl.empty("ProjectContext");
        }
        return new FilesystemProjectContextProvider(props.getWorkDir());
    }
}
