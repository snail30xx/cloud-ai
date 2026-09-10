package com.cloudai.core.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 模型响应 — 包含一个或多个候选结果。
 *
 * <p>单候选场景（当前主流）可通过 {@link #content()} / {@link #toolCalls()} / {@link #finishReason()}
 * 便捷访问第一个候选结果。</p>
 *
 * @param id          响应唯一标识（provider 返回），流式聚合前可能为 null
 * @param model       实际使用的模型名，流式聚合前可能为 null
 * @param generations 候选结果列表
 * @param usage       Token 统计
 * @param metadata    响应级元数据（rate limit、reasoning 等）
  * @author cloud-ai
 * @since 1.0
*/
public record ChatResponse(
        @Nullable String id,
        @Nullable String model,
        List<Generation> generations,
        TokenUsage usage,
        Map<String, Object> metadata) {

    public ChatResponse {
        generations = generations != null ? List.copyOf(generations) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /** 便捷工厂：单候选结果  * @author cloud-ai
 * @since 1.0
*/
    public static ChatResponse of(String content, List<ToolCall> toolCalls,
                                   TokenUsage usage, FinishReason finishReason) {
        return new ChatResponse(null, null,
                List.of(new Generation(content, toolCalls, finishReason, Map.of())),
                usage, Map.of());
    }

    /** 便捷工厂：单候选结果 + 响应元数据  * @author cloud-ai
 * @since 1.0
*/
    public static ChatResponse of(@Nullable String id, @Nullable String model, String content,
                                   List<ToolCall> toolCalls, TokenUsage usage,
                                   FinishReason finishReason, Map<String, Object> metadata) {
        return new ChatResponse(id, model,
                List.of(new Generation(content, toolCalls, finishReason, Map.of())),
                usage, metadata);
    }

    // ==================== 单候选便捷访问 ====================

    /** 第一个候选的文本内容  * @author cloud-ai
 * @since 1.0
*/
    public String content() {
        return generations.isEmpty() ? "" : generations.get(0).content();
    }

    /** 第一个候选的工具调用  * @author cloud-ai
 * @since 1.0
*/
    public List<ToolCall> toolCalls() {
        return generations.isEmpty() ? List.of() : generations.get(0).toolCalls();
    }

    /** 第一个候选的结束原因  * @author cloud-ai
 * @since 1.0
*/
    public FinishReason finishReason() {
        return generations.isEmpty() ? FinishReason.STOP : generations.get(0).finishReason();
    }
}
