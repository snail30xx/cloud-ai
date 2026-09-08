package com.cloudai.llm.client;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.llm.advisor.Advisor;
import com.cloudai.llm.client.spec.ChatClientRequestSpec;
import com.cloudai.llm.client.support.DefaultChatClientRequestSpec;
import com.cloudai.llm.client.support.ModelRouterAccessor;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * ChatClient — LLM 调用的流式入口。
 *
 * <p>通过 {@link ChatClientFactory} 创建：</p>
 *
 * <pre>{@code
 * ChatClient client = ChatClientFactory.builder(router)
 *     .observationRegistry(registry)
 *     .defaultAdvisors(new LoggingAdvisor())
 *     .build();
 *
 * String reply = client.prompt("Hello")
 *     .call()
 *     .content();
 * }</pre>
 *
 * <p>扩展点：subclass {@link ChatClientBuilder} 或 {@link ChatClient} 本身。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ChatClient {

    private final ModelRouterAccessor router;
    private final ObservationRegistry observationRegistry;
    private final List<Advisor> defaultAdvisors;

    ChatClient(ModelRouterAccessor router, @Nullable ObservationRegistry observationRegistry,
               @Nullable List<Advisor> defaultAdvisors) {
        this.router = router;
        this.observationRegistry = observationRegistry != null
                ? observationRegistry : ObservationRegistry.NOOP;
        this.defaultAdvisors = List.copyOf(defaultAdvisors != null ? defaultAdvisors : List.of());
    }

    // ==================== prompt ====================

    public ChatClientRequestSpec prompt(String content) {
        return createRequestSpec().user(content);
    }

    public ChatClientRequestSpec prompt(ChatRequest request) {
        return createRequestSpec(request);
    }

    protected ChatClientRequestSpec createRequestSpec() {
        return new DefaultChatClientRequestSpec(router, defaultAdvisors);
    }

    protected ChatClientRequestSpec createRequestSpec(ChatRequest request) {
        return new DefaultChatClientRequestSpec(router, defaultAdvisors, request);
    }

    // ==================== getters ====================

    protected ModelRouterAccessor router() { return router; }
    protected ObservationRegistry observationRegistry() { return observationRegistry; }
    protected List<Advisor> defaultAdvisors() { return defaultAdvisors; }
}