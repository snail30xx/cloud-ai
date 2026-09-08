package com.cloudai.llm.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.common.KeyValues;

import static com.cloudai.llm.observation.ChatModelObservationDocumentation.*;

/**
 * 默认 LLM 聊天观测约定 — 将 {@link ChatModelObservationContext} 上下文信息
 * 转换为 OpenTelemetry Gen AI 语义约定兼容的 KeyValue 标签。
 *
 * <p>Low cardinality: provider、model、streaming、message_count（用于分组/聚合）。</p>
 * <p>High cardinality: input_tokens、output_tokens、finish_reason（用于统计/分析）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultChatModelObservationConvention implements ObservationConvention<ChatModelObservationContext> {

    public static final String OBSERVATION_NAME = ChatModelObservationDocumentation.OBSERVATION_NAME;

    public static final DefaultChatModelObservationConvention INSTANCE = new DefaultChatModelObservationConvention();

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ChatModelObservationContext ctx) {
        return KeyValues.of(
                system(ctx.getProvider()),
                requestModel(ctx.getModel()),
                streaming(ctx.isStreaming()),
                messageCount(ctx.getMessageCount())
        );
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ChatModelObservationContext ctx) {
        if (ctx.getResponse() != null) {
            var r = ctx.getResponse();
            return KeyValues.of(
                    inputTokens(r.usage().inputTokens()),
                    outputTokens(r.usage().outputTokens()),
                    finishReason(r.finishReason().name())
            );
        }
        return KeyValues.empty();
    }
}