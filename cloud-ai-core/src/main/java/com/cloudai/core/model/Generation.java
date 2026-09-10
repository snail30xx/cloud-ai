package com.cloudai.core.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 单个候选结果。
 *
 * @param content      文本内容
 * @param toolCalls    工具调用
 * @param finishReason 结束原因
 * @param metadata     候选级元数据（reasoning content、logprobs 等）
  * @author cloud-ai
 * @since 1.0
*/
public record Generation(
        @Nullable String content,
        @Nullable List<ToolCall> toolCalls,
        @Nullable FinishReason finishReason,
        @Nullable Map<String, Object> metadata) {

    public Generation {
        content = content != null ? content : "";
        toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
