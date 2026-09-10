package com.cloudai.spring.config;

import com.cloudai.memory.config.MemoryProperties;
import com.cloudai.memory.ContextManager;
import com.cloudai.memory.MemoryRetriever;
import com.cloudai.memory.MemoryStore;
import com.cloudai.spring.properties.CloudAiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.memory.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MemoryProperties memoryProperties(CloudAiProperties props) {
        var mem = props.memory() != null ? props.memory() : new CloudAiProperties.Memory();
        return new MemoryProperties(
                mem.maxContextTokens(),
                mem.maxRetrievalResults(),
                mem.defaultAgentId());
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryStore memoryStore() {
        return com.cloudai.memory.config.MemoryAutoConfiguration.memoryStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryRetriever memoryRetriever(MemoryStore memoryStore) {
        return com.cloudai.memory.config.MemoryAutoConfiguration.memoryRetriever(memoryStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextManager contextManager() {
        return com.cloudai.memory.config.MemoryAutoConfiguration.contextManager();
    }
}