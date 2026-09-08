package com.cloudai.core.spi;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 模型调用接口 — 抽象的 LLM 调用能力。
 *
 * <p>这是 cloud-ai-core 定义的最窄契约：只包含运行时调用路径。
 * 模型发现（{@code getModelInfo} / {@code listModels}）由上层模块的可选接口提供。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface ChatModel {

    /** 同步调用 */
    ChatResponse call(ChatRequest request);

    /** 流式调用（SSE），默认回退到同步调用 */
    default Flux<ChatResponse> stream(ChatRequest request) {
        return Flux.just(call(request));
    }
}