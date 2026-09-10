package com.cloudai.spring.config;

import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.memory.MemoryRetriever;
import com.cloudai.memory.MemoryStore;
import com.cloudai.persona.PersonaAssembler;
import com.cloudai.persona.PersonaProvider;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.server.service.AgentService;
import com.cloudai.skills.SkillRegistry;
import com.cloudai.spring.interceptor.ApiKeyInterceptor;
import com.cloudai.spring.properties.CloudAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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

    private final CloudAiProperties props;

    public ServerAutoConfiguration(CloudAiProperties props) {
        this.props = props;
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyInterceptor apiKeyInterceptor() {
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
            ObjectProvider<SkillRegistry> skillRegistryProvider,
            Path workDir) {
        // skills 模块可被禁用（cloud-ai.skills.enabled=false），此时技能菜单不注入
        var skillRegistry = skillRegistryProvider.getIfAvailable();
        log.info("Spring wiring AgentService (skillRegistry={})",
                skillRegistry != null ? "present" : "absent");
        return new AgentService(agentLoop, personaProvider, personaAssembler,
                memoryRetriever, memoryStore, toolRegistry, skillRegistry, workDir);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var server = props.server() != null ? props.server() : new CloudAiProperties.Server(null);
        registry.addInterceptor(new ApiKeyInterceptor(server.apiKey()))
                .addPathPatterns("/api/**");
    }
}