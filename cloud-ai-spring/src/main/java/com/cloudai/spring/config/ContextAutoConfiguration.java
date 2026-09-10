package com.cloudai.spring.config;

import com.cloudai.context.config.ContextProperties;
import com.cloudai.context.spi.ContextProvider;
import com.cloudai.spring.properties.CloudAiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.context.enabled", havingValue = "true", matchIfMissing = true)
public class ContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ContextProperties contextProperties(CloudAiProperties props) {
        var ctx = props.context() != null ? props.context() : new CloudAiProperties.Context();
        var p = new ContextProperties();
        if (ctx.workDir() != null) {
            p.setWorkDir(ctx.workDir());
        }
        p.setProjectContextEnabled(ctx.projectContextEnabled());
        return p;
    }

    @Bean
    @ConditionalOnMissingBean(name = "environmentProvider")
    public ContextProvider environmentProvider(ContextProperties props) {
        return com.cloudai.context.config.ContextAutoConfiguration.environmentProvider(props);
    }

    @Bean
    @ConditionalOnMissingBean(name = "projectContextProvider")
    public ContextProvider projectContextProvider(ContextProperties props) {
        return com.cloudai.context.config.ContextAutoConfiguration.projectContextProvider(props);
    }
}