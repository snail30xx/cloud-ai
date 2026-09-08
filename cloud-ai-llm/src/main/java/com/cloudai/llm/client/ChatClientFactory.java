package com.cloudai.llm.client;

import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.advisor.Advisor;
import com.cloudai.llm.client.support.ModelRouterAdapter;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * ChatClient 工厂 — 唯一的公开入口。
 *
 * <pre>{@code
 * // 快捷创建
 * ChatClient client = ChatClientFactory.create(router);
 *
 * // Builder 创建
 * ChatClient client = ChatClientFactory.builder(router)
 *     .observationRegistry(registry)
 *     .defaultAdvisors(new LoggingAdvisor())
 *     .build();
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class ChatClientFactory {

    private ChatClientFactory() {}

    /** 快捷创建（无观测、无 advisors） */
    public static ChatClient create(ModelRouter router) {
        return builder(router).build();
    }

    /** 快捷创建（带观测） */
    public static ChatClient create(ModelRouter router, @Nullable ObservationRegistry observationRegistry) {
        return builder(router).observationRegistry(observationRegistry).build();
    }

    /** 快捷创建（带观测和 advisors） */
    public static ChatClient create(ModelRouter router, @Nullable ObservationRegistry observationRegistry,
                                    @Nullable List<Advisor> advisors) {
        return builder(router).observationRegistry(observationRegistry).defaultAdvisors(advisors).build();
    }

    /** 进入 Builder 模式 */
    public static ChatClientBuilder builder(ModelRouter router) {
        return new ChatClientBuilder(new ModelRouterAdapter(router));
    }
}