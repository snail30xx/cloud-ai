package com.cloudai.spring.config;

import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.memory.spi.MemoryRetriever;
import com.cloudai.memory.spi.MemoryStore;
import com.cloudai.persona.spi.PersonaAssembler;
import com.cloudai.persona.spi.PersonaProvider;
import com.cloudai.runtime.spi.AgentLoop;
import com.cloudai.server.service.AgentService;
import com.cloudai.spring.interceptor.ApiKeyInterceptor;
import com.cloudai.spring.properties.CloudAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.server.enabled", havingValue = "true", matchIfMissing = true)
public class ServerAutoConfiguration implements WebMvcConfigurer {
    private static final Logger log = LoggerFactory.getLogger(ServerAutoConfiguration.class);

    private final ApiKeyInterceptor apiKeyInterceptor;

    public ServerAutoConfiguration(ApiKeyInterceptor apiKeyInterceptor) {
        this.apiKeyInterceptor = apiKeyInterceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyInterceptor apiKeyInterceptor(CloudAiProperties props) {
        var server = props.server() != null ? props.server() : new CloudAiProperties.Server(null);
        return new ApiKeyInterceptor(server.apiKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentService agentService(
            AgentLoop agentLoop,
            PersonaProvider personaProvider,
            PersonaAssembler personaAssembler,
            MemoryRetriever memoryRetriever,
            MemoryStore memoryStore,
            ToolRegistry toolRegistry,
            Path workDir) {
        log.info("Spring wiring AgentService");
        return new AgentService(agentLoop, personaProvider, personaAssembler,
                memoryRetriever, memoryStore, toolRegistry, null, workDir);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns("/api/**");
    }
}