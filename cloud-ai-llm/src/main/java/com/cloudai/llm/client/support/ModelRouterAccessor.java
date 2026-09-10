package com.cloudai.llm.client.support;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.llm.ModelRouter;
import reactor.core.publisher.Flux;

/**
 * 模型路由访问抽象 — 解耦 ChatClient 与 {@link ModelRouter}，限定 client 层只暴露必要操作。
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface ModelRouterAccessor {

    ChatResponse chat(String providerName, ChatRequest request);

    ChatResponse chatDefault(ChatRequest request);

    Flux<ChatResponse> stream(String providerName, ChatRequest request);

    Flux<ChatResponse> streamDefault(ChatRequest request);
}