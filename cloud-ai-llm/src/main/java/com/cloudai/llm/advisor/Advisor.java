package com.cloudai.llm.advisor;

import com.cloudai.core.chat.ChatRequest;

/**
 * Advisor — 请求拦截器接口。
 *
 * <p>Advisor 可以在请求发送前修改请求参数，实现日志、监控、内容过滤等横切关注点。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@FunctionalInterface
public interface Advisor {

    /**
     * 在请求发送前拦截并修改请求。
     *
     * @param request 原始请求
     * @param chain   责任链，调用 {@code chain.next(request)} 继续传递
     * @return 修改后的请求
     */
    ChatRequest advise(ChatRequest request, AdvisorChain chain);

    /**
     * 责任链 — 将多个 Advisor 串联执行。
     */
    interface AdvisorChain {
        /**
         * 传递给下一个 Advisor。
         *
         * @param request 当前请求
         * @return 处理后的请求
         */
        ChatRequest next(ChatRequest request);
    }
}