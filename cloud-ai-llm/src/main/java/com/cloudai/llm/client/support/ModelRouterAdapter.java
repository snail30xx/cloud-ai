package com.cloudai.llm.client.support;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.ChatResponse;
import com.cloudai.llm.ModelRouter;
import reactor.core.publisher.Flux;

/**
 * {@link ModelRouterAccessor} 实现 — 将 {@link ModelRouter} 适配为内部接口。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ModelRouterAdapter implements ModelRouterAccessor {

    private final ModelRouter router;

    public ModelRouterAdapter(ModelRouter router) {
        this.router = router;
    }

    @Override
    public ChatResponse chat(String providerName, ChatRequest request) {
        return router.chat(providerName, request);
    }

    @Override
    public ChatResponse chatDefault(ChatRequest request) {
        return router.chatDefault(request);
    }

    @Override
    public Flux<ChatResponse> stream(String providerName, ChatRequest request) {
        return router.stream(providerName, request);
    }

    @Override
    public Flux<ChatResponse> streamDefault(ChatRequest request) {
        return router.streamDefault(request);
    }
}