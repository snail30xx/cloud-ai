package com.cloudai.llm.adapter;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.llm.model.OpenAiChatRequest;
import com.cloudai.llm.observation.ChatModelObservationContext;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

/**
 * DeepSeek 适配器 — 支持 DeepSeek 特有的 reasoning_effort 参数。
 *
 * <p>DeepSeek API 兼容 OpenAI 协议，额外支持：</p>
 * <ul>
 *   <li>{@code reasoning_effort}: "low" / "medium" / "high"，控制思考强度</li>
 *   <li>{@code thinking.type}: "enabled" / "disabled"，是否启用思考模式</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DeepSeekLlmAdapter extends AbstractLlmAdapter {

    private final boolean thinkingEnabled;
    private final String reasoningEffort;

    public DeepSeekLlmAdapter(ProviderProperties props) {
        this(props, ObservationRegistry.NOOP, null);
    }

    public DeepSeekLlmAdapter(ProviderProperties props, @Nullable ObservationRegistry observationRegistry) {
        this(props, observationRegistry, null);
    }

    public DeepSeekLlmAdapter(ProviderProperties props, @Nullable ObservationRegistry observationRegistry,
                              @Nullable ObservationConvention<ChatModelObservationContext> convention) {
        super(props, "deepseek", observationRegistry, convention);
        this.thinkingEnabled = props.capabilities().contains("thinking");
        this.reasoningEffort = props.capabilities().contains("thinking") ? "high" : null;
    }

    @Override
    protected OpenAiChatRequest buildRequestBody(ChatRequest request) {
        var body = super.buildRequestBody(request);

        if (!thinkingEnabled) {
            return body;
        }

        return body.withReasoningEffort(reasoningEffort)
                .withThinking(new OpenAiChatRequest.Thinking("enabled"));
    }
}