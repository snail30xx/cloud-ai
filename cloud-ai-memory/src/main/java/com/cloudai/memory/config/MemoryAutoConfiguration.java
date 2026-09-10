package com.cloudai.memory.config;

import com.cloudai.memory.advisor.MemoryAdvisor;
import com.cloudai.memory.InMemoryMemoryStore;
import com.cloudai.memory.KeywordMemoryRetriever;
import com.cloudai.memory.SimpleContextManager;
import com.cloudai.memory.ContextManager;
import com.cloudai.memory.MemoryRetriever;
import com.cloudai.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记忆模块工厂 — 替代 Spring 自动装配。
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class MemoryAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MemoryAutoConfiguration.class);

    private MemoryAutoConfiguration() {}

    public static MemoryStore memoryStore() {
        log.info("Creating InMemoryMemoryStore");
        return new InMemoryMemoryStore();
    }

    public static MemoryRetriever memoryRetriever(MemoryStore memoryStore) {
        log.info("Creating KeywordMemoryRetriever");
        return new KeywordMemoryRetriever(memoryStore);
    }

    public static ContextManager contextManager() {
        log.info("Creating SimpleContextManager");
        return new SimpleContextManager();
    }

    public static MemoryAdvisor memoryAdvisor(MemoryRetriever retriever, MemoryProperties properties) {
        log.info("Creating MemoryAdvisor for agent '{}', maxResults={}",
                properties.defaultAgentId(), properties.maxRetrievalResults());
        return new MemoryAdvisor(retriever, properties.defaultAgentId(), properties.maxRetrievalResults());
    }
}