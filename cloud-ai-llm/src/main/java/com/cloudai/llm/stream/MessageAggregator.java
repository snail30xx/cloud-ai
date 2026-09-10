package com.cloudai.llm.stream;

import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.TokenUsage;
import com.cloudai.core.tool.ToolCall;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式响应聚合器 — 将 {@code Flux<ChatResponse>} 流式 chunk 聚合为单一完整响应。
 *
 * <p>参考 Spring AI 的 {@code MessageAggregator} 设计模式，使用 {@link AtomicReference}
 * 保证线程安全的内容累积。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class MessageAggregator {

    private MessageAggregator() {
    }

    /**
     * 聚合流式 chunk 为最终 {@link ChatResponse}。
     *
     * @param source 流式 chunk 源
     * @return 聚合后的 Flux（仅 emit 一次最终结果）
     */
    public static Flux<ChatResponse> aggregate(Flux<ChatResponse> source) {
        var contentRef = new AtomicReference<>(new StringBuilder());
        var toolCallsRef = new AtomicReference<>(new ArrayList<ToolCall>());
        var usageRef = new AtomicReference<>(new TokenUsage(0, 0));
        var finishReasonRef = new AtomicReference<>(FinishReason.STOP);
        var idRef = new AtomicReference<@Nullable String>();
        var modelRef = new AtomicReference<@Nullable String>();

        return source
                .doOnNext(chunk -> {
                    if (chunk.id() != null && idRef.get() == null) {
                        idRef.set(chunk.id());
                    }
                    if (chunk.model() != null && modelRef.get() == null) {
                        modelRef.set(chunk.model());
                    }
                    if (chunk.content() != null && !chunk.content().isEmpty()) {
                        contentRef.get().append(chunk.content());
                    }
                    if (chunk.toolCalls() != null && !chunk.toolCalls().isEmpty()) {
                        mergeToolCalls(toolCallsRef, chunk.toolCalls());
                    }
                    if (chunk.usage() != null && chunk.usage().totalTokens() > 0) {
                        usageRef.set(chunk.usage());
                    }
                    if (chunk.finishReason() != null && chunk.finishReason() != FinishReason.STOP) {
                        finishReasonRef.set(chunk.finishReason());
                    }
                })
                .then(Mono.fromCallable(() -> ChatResponse.of(
                        idRef.get(), modelRef.get(),
                        contentRef.get().toString(),
                        List.copyOf(toolCallsRef.get()),
                        usageRef.get(),
                        finishReasonRef.get(),
                        java.util.Map.of()
                )))
                .flux();
    }

    /**
     * 按索引合并流式工具调用 chunk。
     *
     * <p>流式工具调用通常分多个 chunk 传输：
     * <ul>
     *   <li>第一个 chunk 包含 id + name + 空 arguments</li>
     *   <li>后续 chunk 仅包含增量 arguments（id 和 name 为空）</li>
     * </ul>
     * 按数组索引合并：首 chunk 设置 identity，后续 chunk 追加 arguments。</p>
     */
    private static void mergeToolCalls(AtomicReference<ArrayList<ToolCall>> ref, List<ToolCall> chunkToolCalls) {
        var list = ref.get();
        for (int i = 0; i < chunkToolCalls.size(); i++) {
            var tc = chunkToolCalls.get(i);
            if (tc.hasIdentity()) {
                if (i < list.size()) {
                    list.set(i, tc);
                } else {
                    while (list.size() < i) {
                        list.add(new ToolCall("", "", ""));
                    }
                    list.add(tc);
                }
            } else {
                if (i < list.size()) {
                    var existing = list.get(i);
                    list.set(i, new ToolCall(
                            existing.id(), existing.name(),
                            existing.arguments() + tc.arguments()));
                }
            }
        }
    }
}