package com.cloudai.llm.client;

import com.cloudai.llm.advisor.Advisor;
import com.cloudai.llm.client.support.ModelRouterAccessor;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ChatClient} 构建器 — 主要扩展点。
 *
 * <p>用户可 subclass 此构建器来注入自定义行为，也可通过构造器直接创建：</p>
 *
 * <pre>{@code
 * // 标准用法
 * var client = ChatClient.builder(router)
 *     .observationRegistry(registry)
 *     .defaultAdvisors(new LoggingAdvisor())
 *     .build();
 *
 * // 扩展用法
 * var client = new ChatClientBuilder(router) {
 *     &#64;Override
 *     protected ChatClient createClient() {
 *         return new MyChatClient(router(), observationRegistry(), defaultAdvisors());
 *     }
 * }.build();
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ChatClientBuilder {

    private final ModelRouterAccessor router;
    private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
    private final List<Advisor> defaultAdvisors = new ArrayList<>();

    public ChatClientBuilder(ModelRouterAccessor router) {
        this.router = router;
    }

    public ChatClientBuilder observationRegistry(@Nullable ObservationRegistry registry) {
        this.observationRegistry = registry != null ? registry : ObservationRegistry.NOOP;
        return this;
    }

    public ChatClientBuilder defaultAdvisors(@Nullable Advisor... advisors) {
        if (advisors != null) {
            this.defaultAdvisors.addAll(List.of(advisors));
        }
        return this;
    }

    public ChatClientBuilder defaultAdvisors(@Nullable List<Advisor> advisors) {
        if (advisors != null) {
            this.defaultAdvisors.addAll(advisors);
        }
        return this;
    }

    /** 子类访问器 */
    protected ModelRouterAccessor router() { return router; }
    protected ObservationRegistry observationRegistry() { return observationRegistry; }
    protected List<Advisor> defaultAdvisors() { return List.copyOf(defaultAdvisors); }

    /**
     * 构建 ChatClient。子类覆盖 {@link #createClient()} 来控制实例化。
     */
    public final ChatClient build() {
        return createClient();
    }

    /**
     * 工厂方法 — 子类覆盖以返回自定义 ChatClient。
     */
    protected ChatClient createClient() {
        return new ChatClient(router, observationRegistry, defaultAdvisors);
    }
}