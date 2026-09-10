package com.cloudai.llm;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.ModelInfo;
import com.cloudai.core.chat.ChatModel;
import com.cloudai.core.chat.ModelDiscovery;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型路由器 — 根据 provider 名称路由到对应的 {@link ChatModel} 实例。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ModelRouter {
    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final String defaultProvider;

    public ModelRouter(@Nullable String defaultProvider) {
        if (defaultProvider == null || defaultProvider.isBlank()) {
            throw new IllegalArgumentException("defaultProvider must not be blank");
        }
        this.defaultProvider = defaultProvider;
    }

    /**
     * 注册模型。
     *
     * @throws IllegalArgumentException 如果 name 为空或 model 为 null
     * @throws IllegalStateException    如果 name 已注册
     */
    public void register(@Nullable String name, @Nullable ChatModel model) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Model name must not be blank");
        }
        if (model == null) {
            throw new IllegalArgumentException("ChatModel must not be null for: " + name);
        }
        var previous = models.putIfAbsent(name, model);
        if (previous != null) {
            throw new IllegalStateException(
                    "Model '" + name + "' is already registered. Duplicate registration is not allowed.");
        }
        log.info("Model registered: {}", name);
    }

    /** 获取默认模型 */
    public ChatModel getDefault() {
        return resolve(defaultProvider);
    }

    /** 按 provider 名获取模型 */
    public ChatModel resolve(String name) {
        var model = models.get(name);
        if (model == null) {
            throw new IllegalStateException("No model found for: " + name
                    + ". Available: " + models.keySet());
        }
        return model;
    }

    /** 同步调用指定模型 */
    public ChatResponse chat(String name, ChatRequest request) {
        return resolve(name).call(request);
    }

    /** 同步调用默认模型 */
    public ChatResponse chatDefault(ChatRequest request) {
        return getDefault().call(request);
    }

    /** 流式调用指定模型 */
    public Flux<ChatResponse> stream(String name, ChatRequest request) {
        return resolve(name).stream(request);
    }

    /** 流式调用默认模型 */
    public Flux<ChatResponse> streamDefault(ChatRequest request) {
        return getDefault().stream(request);
    }

    /**
     * 获取所有 provider 的可用模型列表（调用实现了 {@link ModelDiscovery} 的模型的 {@code listModels()}）。
     */
    public List<ModelInfo> listModels() {
        return models.values().stream()
                .filter(m -> m instanceof ModelDiscovery)
                .flatMap(m -> ((ModelDiscovery) m).listModels().stream())
                .sorted(Comparator.comparing(ModelInfo::provider)
                        .thenComparing(ModelInfo::model))
                .toList();
    }

    /**
     * 启动时校验：确保默认 provider 已注册。
     *
     * @throws IllegalStateException 如果默认 provider 未注册
     */
    public void validate() {
        if (!models.containsKey(defaultProvider)) {
            throw new IllegalStateException(
                    "Default model '" + defaultProvider + "' is not registered. "
                            + "Registered models: " + models.keySet());
        }
        log.info("ModelRouter validated: {} model(s) registered, default='{}'",
                models.size(), defaultProvider);
    }
}