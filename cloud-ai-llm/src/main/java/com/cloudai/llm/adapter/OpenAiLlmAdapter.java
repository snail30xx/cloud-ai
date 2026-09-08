package com.cloudai.llm.adapter;

import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.llm.observation.ChatModelObservationContext;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

/**
 * OpenAI 适配器。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class OpenAiLlmAdapter extends AbstractOpenAiCompatibleAdapter {

    public OpenAiLlmAdapter(ProviderProperties props) {
        this(props, ObservationRegistry.NOOP, null);
    }

    public OpenAiLlmAdapter(ProviderProperties props, @Nullable ObservationRegistry observationRegistry) {
        this(props, observationRegistry, null);
    }

    public OpenAiLlmAdapter(ProviderProperties props, @Nullable ObservationRegistry observationRegistry,
                            @Nullable ObservationConvention<ChatModelObservationContext> convention) {
        super(props, "openai", observationRegistry, convention);
    }
}