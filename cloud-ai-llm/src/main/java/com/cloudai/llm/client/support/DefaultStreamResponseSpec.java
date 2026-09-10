package com.cloudai.llm.client.support;

import com.cloudai.core.chat.ChatResponse;
import com.cloudai.llm.client.spec.StreamResponseSpec;
import com.cloudai.llm.stream.MessageAggregator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link StreamResponseSpec} 默认实现。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultStreamResponseSpec implements StreamResponseSpec {
    private final Flux<ChatResponse> flux;

    public DefaultStreamResponseSpec(Flux<ChatResponse> flux) {
        this.flux = flux;
    }

    @Override
    public Flux<String> content() {
        return flux.map(ChatResponse::content);
    }

    @Override
    public Flux<ChatResponse> chatResponse() {
        return flux;
    }

    @Override
    public Mono<ChatResponse> aggregate() {
        return MessageAggregator.aggregate(flux).next();
    }
}