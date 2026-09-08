package com.cloudai.llm.client.spec;

import com.cloudai.core.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 流式调用响应规格。
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface StreamResponseSpec {

    /** 获取流式文本内容 */
    Flux<String> content();

    /** 获取流式 ChatResponse（逐 chunk） */
    Flux<ChatResponse> chatResponse();

    /** 聚合所有 chunk 为最终响应的 Mono */
    Mono<ChatResponse> aggregate();
}