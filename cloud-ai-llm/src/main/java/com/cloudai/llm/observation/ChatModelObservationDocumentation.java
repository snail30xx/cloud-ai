package com.cloudai.llm.observation;

import io.micrometer.common.KeyValue;

/**
 * LLM 观测指标 Key 定义，遵循 OpenTelemetry Gen AI 语义约定。
 *
 * <p>Low cardinality keys（标签维度）和 High cardinality keys（值维度）分离，
 * 供 {@link DefaultChatModelObservationConvention} 使用。</p>
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/gen-ai/">OpenTelemetry Gen AI</a>
 */
public final class ChatModelObservationDocumentation {

    private ChatModelObservationDocumentation() {}

    /** Observation 名称 */
    public static final String OBSERVATION_NAME = "cloud-ai.llm.chat";

    // ==================== Low Cardinality Keys ====================

    /** Gen AI 系统（provider 名，如 "openai"、"deepseek"） */
    public static final String LOW_GEN_AI_SYSTEM = "gen_ai.system";

    /** 请求模型名 */
    public static final String LOW_GEN_AI_REQUEST_MODEL = "gen_ai.request.model";

    /** 是否流式调用 */
    public static final String LOW_GEN_AI_STREAMING = "gen_ai.streaming";

    /** 请求消息数 */
    public static final String LOW_MESSAGE_COUNT = "cloud_ai.message_count";

    // ==================== High Cardinality Keys ====================

    /** 输入 token 数 */
    public static final String HIGH_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";

    /** 输出 token 数 */
    public static final String HIGH_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    /** 响应结束原因 */
    public static final String HIGH_RESPONSE_FINISH_REASON = "gen_ai.response.finish_reason";

    // ==================== 便捷工厂方法 ====================

    public static KeyValue system(String provider) {
        return KeyValue.of(LOW_GEN_AI_SYSTEM, provider);
    }

    public static KeyValue requestModel(String model) {
        return KeyValue.of(LOW_GEN_AI_REQUEST_MODEL, model);
    }

    public static KeyValue streaming(boolean streaming) {
        return KeyValue.of(LOW_GEN_AI_STREAMING, String.valueOf(streaming));
    }

    public static KeyValue messageCount(int count) {
        return KeyValue.of(LOW_MESSAGE_COUNT, String.valueOf(count));
    }

    public static KeyValue inputTokens(long tokens) {
        return KeyValue.of(HIGH_USAGE_INPUT_TOKENS, String.valueOf(tokens));
    }

    public static KeyValue outputTokens(long tokens) {
        return KeyValue.of(HIGH_USAGE_OUTPUT_TOKENS, String.valueOf(tokens));
    }

    public static KeyValue finishReason(String reason) {
        return KeyValue.of(HIGH_RESPONSE_FINISH_REASON, reason);
    }
}