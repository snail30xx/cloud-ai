package com.cloudai.spring.config;

import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.config.LlmProperties;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.spring.properties.CloudAiProperties;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.llm.enabled", havingValue = "true", matchIfMissing = true)
public class LlmAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(LlmAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ModelRouter modelRouter(CloudAiProperties props,
                                   ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        var springLlm = props.llm() != null ? props.llm() : new CloudAiProperties.Llm(null, null);
        var providers = new HashMap<String, ProviderProperties>();
        if (springLlm.providers() != null) {
            for (var entry : springLlm.providers().entrySet()) {
                var p = entry.getValue();
                providers.put(entry.getKey(), new ProviderProperties(
                        p.baseUrl(), p.apiKey(), p.model(),
                        p.timeout(), p.maxContextTokens(), p.maxRetries(), p.capabilities()));
            }
        }
        var llmProps = new LlmProperties(springLlm.defaultProvider(), providers);
        // ObservationRegistry 可选（未引入 actuator 时回退 NOOP）
        var observationRegistry = observationRegistryProvider.getIfAvailable();
        log.info("Spring wiring LLM: defaultProvider='{}', providers={}, observation={}",
                springLlm.defaultProvider(), providers.keySet(),
                observationRegistry != null ? "enabled" : "noop");
        return com.cloudai.llm.config.LlmAutoConfiguration.modelRouter(llmProps, observationRegistry, null);
    }
}