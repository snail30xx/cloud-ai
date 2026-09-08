package com.cloudai.llm.config;

import com.cloudai.core.spi.ChatModel;
import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.adapter.DeepSeekLlmAdapter;
import com.cloudai.llm.adapter.OpenAiLlmAdapter;
import com.cloudai.llm.observation.ChatModelObservationContext;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * LLM 自动装配 — 读取配置、创建适配器、注册到路由器。
 *
 * <p>根据 provider 的 {@code capabilities} 或名称自动选择适配器类型：
 * <ul>
 *   <li>capabilities 包含 "thinking" → {@link DeepSeekLlmAdapter}</li>
 *   <li>其他 → {@link OpenAiLlmAdapter}</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
@ConditionalOnProperty(name = "cloud-ai.llm.enabled", havingValue = "true", matchIfMissing = true)
public class LlmAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(LlmAutoConfiguration.class);

    private final LlmProperties llmProperties;
    private final ObservationRegistry observationRegistry;
    private final ObservationConvention<ChatModelObservationContext> observationConvention;

    public LlmAutoConfiguration(LlmProperties llmProperties,
                                ObjectProvider<ObservationRegistry> observationRegistryProvider,
                                ObjectProvider<ObservationConvention<ChatModelObservationContext>> conventionProvider) {
        this.llmProperties = llmProperties;
        this.observationRegistry = observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
        this.observationConvention = conventionProvider.getIfAvailable();
    }

    /**
     * 创建并装配 ModelRouter。
     */
    @Bean
    @ConditionalOnMissingBean
    public ModelRouter modelRouter() {
        String defaultProvider = llmProperties.defaultProvider();
        if (defaultProvider == null || defaultProvider.isBlank()) {
            throw new IllegalStateException("cloud-ai.llm.default-provider must be configured");
        }

        var router = new ModelRouter(defaultProvider);

        Map<String, ProviderProperties> providers = llmProperties.providers();
        if (providers.isEmpty()) {
            throw new IllegalStateException("No LLM providers configured under cloud-ai.llm.providers");
        }

        for (var entry : providers.entrySet()) {
            String name = entry.getKey();
            ProviderProperties props = entry.getValue();

            // 启动时校验每个 provider 配置
            props.validate();

            ChatModel adapter = createAdapter(name, props);
            router.register(name, adapter);
            log.info("LLM provider '{}' registered: type={}, model={}, baseUrl={}, timeout={}",
                    name, adapter.getClass().getSimpleName(), props.model(),
                    props.baseUrl(), props.timeout());
        }

        // 启动时校验默认模型存在
        router.validate();
        log.info("LLM auto-configuration complete: {} provider(s) registered, default='{}'",
                providers.size(), defaultProvider);

        return router;
    }

    /**
     * 根据 provider 配置创建对应的适配器。
     */
    private ChatModel createAdapter(String name, ProviderProperties props) {
        // 支持 thinking 能力的 provider 使用 DeepSeek 适配器
        if (props.capabilities().contains("thinking")) {
            log.info("Creating DeepSeekLlmAdapter for '{}' (thinking enabled)", name);
            return new DeepSeekLlmAdapter(props, observationRegistry, observationConvention);
        }
        return new OpenAiLlmAdapter(props, observationRegistry, observationConvention);
    }
}