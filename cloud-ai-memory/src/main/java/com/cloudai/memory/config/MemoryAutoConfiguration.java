package com.cloudai.memory.config;

import com.cloudai.memory.advisor.MemoryAdvisor;
import com.cloudai.memory.impl.InMemoryMemoryStore;
import com.cloudai.memory.impl.KeywordMemoryRetriever;
import com.cloudai.memory.impl.SimpleContextManager;
import com.cloudai.memory.spi.ContextManager;
import com.cloudai.memory.spi.MemoryRetriever;
import com.cloudai.memory.spi.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆模块自动装配。
 *
 * <p>通过 {@code cloud-ai.memory.enabled} 控制（默认 true）。
 * 三个核心 Bean 均为 {@link ConditionalOnMissingBean}，允许外部替换。
 * {@link MemoryAdvisor} 可选装配，仅在设置 {@code cloud-ai.memory.advisor.enabled=true} 时创建。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
@ConditionalOnProperty(name = "cloud-ai.memory.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MemoryAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MemoryStore memoryStore() {
        log.info("Creating InMemoryMemoryStore");
        return new InMemoryMemoryStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryRetriever memoryRetriever(MemoryStore memoryStore) {
        log.info("Creating KeywordMemoryRetriever");
        return new KeywordMemoryRetriever(memoryStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextManager contextManager() {
        log.info("Creating SimpleContextManager");
        return new SimpleContextManager();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "cloud-ai.memory.advisor.enabled", havingValue = "true")
    public MemoryAdvisor memoryAdvisor(MemoryRetriever retriever, MemoryProperties properties) {
        log.info("Creating MemoryAdvisor for agent '{}', maxResults={}",
                properties.defaultAgentId(), properties.maxRetrievalResults());
        return new MemoryAdvisor(retriever, properties.defaultAgentId(), properties.maxRetrievalResults());
    }
}
