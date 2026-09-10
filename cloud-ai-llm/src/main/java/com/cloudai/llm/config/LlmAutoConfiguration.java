package com.cloudai.llm.config;

import com.cloudai.core.spi.ChatModel;
import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.adapter.DeepSeekLlmAdapter;
import com.cloudai.llm.adapter.OpenAiLlmAdapter;
import com.cloudai.llm.observation.ChatModelObservationContext;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * LLM 模块工厂 — 替代 Spring 自动装配。
 *
 * <p>根据 provider 的 capabilities 或名称自动选择适配器类型：
 * <ul>
 *   <li>capabilities 包含 "thinking" → DeepSeekLlmAdapter</li>
 *   <li>其他 → OpenAiLlmAdapter</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class LlmAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(LlmAutoConfiguration.class);

    private LlmAutoConfiguration() {}

    /**
     * 创建并装配 ModelRouter。
     *
     * @param llmProperties       LLM 配置
     * @param observationRegistry 观测注册表，null 时使用 NOOP
     * @param convention          观测约定，null 时使用默认
     */
    public static ModelRouter modelRouter(
            LlmProperties llmProperties,
            @Nullable ObservationRegistry observationRegistry,
            @Nullable ObservationConvention<ChatModelObservationContext> convention) {

        String defaultProvider = llmProperties.defaultProvider();
        if (defaultProvider == null || defaultProvider.isBlank()) {
            throw new IllegalStateException("cloud-ai.llm.default-provider must be configured");
        }

        var registry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;

        var router = new ModelRouter(defaultProvider);

        Map<String, ProviderProperties> providers = llmProperties.providers();
        if (providers.isEmpty()) {
            throw new IllegalStateException("No LLM providers configured under cloud-ai.llm.providers");
        }

        for (var entry : providers.entrySet()) {
            String name = entry.getKey();
            ProviderProperties props = entry.getValue();
            props.validate();

            ChatModel adapter = createAdapter(name, props, registry, convention);
            router.register(name, adapter);
            log.info("LLM provider '{}' registered: type={}, model={}, baseUrl={}, timeout={}",
                    name, adapter.getClass().getSimpleName(), props.model(),
                    props.baseUrl(), props.timeout());
        }

        router.validate();
        log.info("LLM configuration complete: {} provider(s) registered, default='{}'",
                providers.size(), defaultProvider);

        return router;
    }

    private static ChatModel createAdapter(String name, ProviderProperties props,
                                           ObservationRegistry registry,
                                           @Nullable ObservationConvention<ChatModelObservationContext> convention) {
        if (props.capabilities().contains("thinking")) {
            log.info("Creating DeepSeekLlmAdapter for '{}' (thinking enabled)", name);
            return new DeepSeekLlmAdapter(props, registry, convention);
        }
        return new OpenAiLlmAdapter(props, registry, convention);
    }
}