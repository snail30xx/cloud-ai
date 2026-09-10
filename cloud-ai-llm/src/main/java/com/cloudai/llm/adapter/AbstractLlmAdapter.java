package com.cloudai.llm.adapter;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.ChatResponse;
import com.cloudai.core.model.FinishReason;
import com.cloudai.core.model.Message;
import com.cloudai.core.model.ModelInfo;
import com.cloudai.core.model.ModelOptions;
import com.cloudai.core.model.TokenUsage;
import com.cloudai.core.model.ToolCall;
import com.cloudai.core.spi.ChatModel;
import com.cloudai.core.spi.ModelDiscovery;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.llm.exception.LlmAuthException;
import com.cloudai.llm.exception.LlmClientException;
import com.cloudai.llm.exception.LlmRateLimitException;
import com.cloudai.llm.exception.LlmServerException;
import com.cloudai.llm.exception.LlmTimeoutException;
import com.cloudai.llm.model.OpenAiChatRequest;
import com.cloudai.llm.model.OpenAiChatResponse;
import com.cloudai.llm.model.OpenAiMessage;
import com.cloudai.llm.model.OpenAiModelListResponse;
import com.cloudai.llm.model.OpenAiTool;
import com.cloudai.llm.model.OpenAiToolCall;
import com.cloudai.llm.observation.ChatModelObservationContext;
import com.cloudai.llm.observation.DefaultChatModelObservationConvention;
import com.cloudai.llm.retry.RetryUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 适配器抽象基类。
 *
 * <p>实现 ChatModel（运行时调用）和 ModelDiscovery（模型发现），
 * 封装通用逻辑：HTTP 调用、请求/响应映射、SSE 解析、观测、重试、异常映射。</p>
 *
 * <p>使用 JDK 内置 {@link HttpClient} 替代 Spring RestClient。</p>
 *
 * <p>子类通过覆盖模板方法适配不同 LLM 协议（OpenAI、DeepSeek、Anthropic 等）：</p>
 * <ul>
 *   <li>{@link #configureRequest(HttpRequest.Builder)} — 认证 header</li>
 *   <li>{@link #getChatEndpoint()} — API 端点路径</li>
 *   <li>{@link #getChatResponseType()} — 响应体类型</li>
 *   <li>{@link #buildRequestBodyInternal(ChatRequest)} — 请求体构建</li>
 *   <li>{@link #parseResponseInternal(Object)} / {@link #parseSseLineInternal(String)} — 响应解析</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public abstract class AbstractLlmAdapter implements ChatModel, ModelDiscovery {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final HttpClient httpClient;
    protected final String provider;
    protected final String model;
    protected final ProviderProperties props;
    private final ObservationRegistry observationRegistry;
    private final ObservationConvention<ChatModelObservationContext> observationConvention;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 缓存从 API 获取的 ModelInfo，null 表示尚未获取或获取失败 */
    @Nullable
    private volatile ModelInfo cachedModelInfo;

    /**
     * @param providerName 提供商标识（如 "openai"、"deepseek"），用于异常信息和 ModelInfo
     */
    protected AbstractLlmAdapter(ProviderProperties props, String providerName,
                                              @Nullable ObservationRegistry observationRegistry,
                                              @Nullable ObservationConvention<ChatModelObservationContext> convention) {
        this.provider = providerName;
        this.model = props.model();
        this.props = props;
        this.observationRegistry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
        this.observationConvention = convention != null ? convention : DefaultChatModelObservationConvention.INSTANCE;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(props.timeout())
                .build();
    }

    /**
     * 子类覆盖此方法添加自定义 header（如认证方式）。
     * 默认添加 OpenAI 兼容的 Authorization: Bearer 头。
     */
    protected void configureRequest(HttpRequest.Builder builder) {
        builder.header("Authorization", "Bearer " + props.apiKey());
    }

    // ==================== 模板方法（子类可覆盖以适配不同协议） ====================

    /** 聊天补全端点路径，默认 /chat/completions（OpenAI 兼容协议） */
    protected String getChatEndpoint() {
        return "/chat/completions";
    }

    /** 聊天响应体类型，用于 JSON 反序列化 */
    protected Class<?> getChatResponseType() {
        return OpenAiChatResponse.class;
    }

    /** 构建请求体，默认委托给 buildRequestBody(ChatRequest) */
    protected Object buildRequestBodyInternal(ChatRequest request) {
        return buildRequestBody(request);
    }

    /** 设置流式标志，返回修改后的请求体 */
    protected Object setStreamFlag(Object body) {
        if (body instanceof OpenAiChatRequest oai) {
            return oai.withStream(true);
        }
        return body;
    }

    /** 预处理请求体（如展开 extraBody），在发送 HTTP 请求前调用 */
    protected Object prepareRequestBody(Object body) {
        if (body instanceof OpenAiChatRequest oai
                && oai.extraBody() != null && !oai.extraBody().isEmpty()) {
            Map<String, Object> bodyMap = OBJECT_MAPPER.convertValue(body,
                    new TypeReference<Map<String, Object>>() {});
            bodyMap.putAll(oai.extraBody());
            bodyMap.remove("extraBody");
            return bodyMap;
        }
        return body;
    }

    /** 解析原始 HTTP 响应为 ChatResponse，默认委托给 parseResponse(OpenAiChatResponse) */
    protected ChatResponse parseResponseInternal(Object rawResponse) {
        return parseResponse((OpenAiChatResponse) rawResponse);
    }

    /** 解析单行 SSE 数据为 ChatResponse，返回 null 表示跳过该行 */
    @Nullable
    protected ChatResponse parseSseLineInternal(String data) throws Exception {
        var chunk = OBJECT_MAPPER.readValue(data, OpenAiChatResponse.class);
        return parseResponse(chunk);
    }

    // ==================== ModelDiscovery ====================

    @Override
    public ModelInfo getModelInfo() {
        if (cachedModelInfo != null) {
            return cachedModelInfo;
        }
        synchronized (this) {
            if (cachedModelInfo != null) {
                return cachedModelInfo;
            }
            cachedModelInfo = fetchModelInfo();
            return cachedModelInfo;
        }
    }

    private ModelInfo fetchModelInfo() {
        try {
            var request = buildGetRequest("/models/" + model);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkHttpStatus(response.statusCode(), response.body());

            var entry = OBJECT_MAPPER.readValue(response.body(),
                    OpenAiModelListResponse.OpenAiModelEntry.class);
            if (entry != null) {
                log.info("Fetched model info from API: id={}, owned_by={}", entry.id(), entry.ownedBy());
                return new ModelInfo(provider, entry.id(), props.maxContextTokens(), props.capabilities());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch model info from API for model '{}', falling back to config: {}",
                    model, e.getMessage());
        }
        return configModelInfo();
    }

    private ModelInfo configModelInfo() {
        return new ModelInfo(provider, model, props.maxContextTokens(), props.capabilities());
    }

    @Override
    public List<ModelInfo> listModels() {
        try {
            var request = buildGetRequest("/models");
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkHttpStatus(response.statusCode(), response.body());

            var listResponse = OBJECT_MAPPER.readValue(response.body(), OpenAiModelListResponse.class);
            if (listResponse != null && listResponse.data() != null) {
                return listResponse.data().stream()
                        .map(entry -> new ModelInfo(provider, entry.id(),
                                props.maxContextTokens(), props.capabilities()))
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Failed to list models from API for provider '{}': {}", provider, e.getMessage());
        }
        return List.of(configModelInfo());
    }

    // ==================== ChatModel: call ====================

    @Override
    public ChatResponse call(ChatRequest request) {
        var body = buildRequestBodyInternal(request);
        log.debug("Chat request: provider={}, model={}, messages={}, tools={}",
                provider, model, request.messages().size(),
                request.tools() != null ? request.tools().size() : 0);

        var ctx = new ChatModelObservationContext(provider, model, request, false);
        var observation = Observation.createNotStarted(
                DefaultChatModelObservationConvention.OBSERVATION_NAME, () -> ctx, observationRegistry)
                .observationConvention(observationConvention);

        return observation.observe(() -> {
            try {
                var response = RetryUtils.executeWithRetry(
                        () -> doChatInternal(body), props.maxRetries(), provider);
                var chatResponse = parseResponseInternal(response);
                ctx.setResponse(chatResponse);
                return chatResponse;
            } catch (LlmAuthException | LlmRateLimitException | LlmServerException | LlmClientException e) {
                throw e;
            } catch (Exception e) {
                if (isTimeoutException(e)) {
                    throw new LlmTimeoutException(provider, "LLM call timed out", e);
                }
                throw new LlmServerException(provider, 0, "LLM call failed: " + e.getMessage());
            }
        });
    }

    protected Object doChatInternal(Object body) {
        Object requestBody = prepareRequestBody(body);

        try {
            var jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);
            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(props.baseUrl() + getChatEndpoint()))
                    .header("Content-Type", "application/json")
                    .timeout(props.timeout())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            configureRequest(requestBuilder);

            log.info("[HTTP Request] POST {}{}", props.baseUrl(), getChatEndpoint());
            if (log.isDebugEnabled()) {
                log.debug("[HTTP Request Body] {}", jsonBody);
            }

            long start = System.currentTimeMillis();
            var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;

            log.info("[HTTP Response] {} -> {} ({}ms)", props.baseUrl(), response.statusCode(), elapsed);

            checkHttpStatus(response.statusCode(), response.body());

            if (log.isDebugEnabled()) {
                log.debug("[HTTP Response Body] {}", response.body());
            }

            return OBJECT_MAPPER.readValue(response.body(), getChatResponseType());
        } catch (LlmAuthException | LlmRateLimitException | LlmServerException | LlmClientException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new LlmTimeoutException(provider, "LLM call timed out", e);
        } catch (Exception e) {
            throw new LlmServerException(provider, 0, "LLM call failed: " + e.getMessage());
        }
    }

    // ==================== ChatModel: stream ====================

    @Override
    public Flux<ChatResponse> stream(ChatRequest request) {
        var body = setStreamFlag(buildRequestBodyInternal(request));

        var ctx = new ChatModelObservationContext(provider, model, request, true);
        var observation = Observation.createNotStarted(
                DefaultChatModelObservationConvention.OBSERVATION_NAME, () -> ctx, observationRegistry)
                .observationConvention(observationConvention);

        final long streamStart = System.currentTimeMillis();
        final int[] chunkCount = {0};

        return Flux.using(
                observation::start,
                scope -> Flux.defer(() -> Flux.<ChatResponse>create(sink -> {
                    try {
                        Object requestBody = prepareRequestBody(body);
                        var jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);

                        var requestBuilder = HttpRequest.newBuilder()
                                .uri(URI.create(props.baseUrl() + getChatEndpoint()))
                                .header("Content-Type", "application/json")
                                .timeout(props.timeout())
                                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                        configureRequest(requestBuilder);

                        var response = httpClient.send(requestBuilder.build(),
                                HttpResponse.BodyHandlers.ofInputStream());
                        checkHttpStatus(response.statusCode(), "");

                        try (var reader = new BufferedReader(
                                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6).trim();
                                    if ("[DONE]".equals(data)) {
                                        break;
                                    }
                                    try {
                                        var chatChunk = parseSseLineInternal(data);
                                        if (chatChunk != null) {
                                            ctx.setResponse(chatChunk);
                                            sink.next(chatChunk);
                                            chunkCount[0]++;
                                        }
                                    } catch (Exception e) {
                                        log.debug("Failed to parse SSE chunk: {}", data, e);
                                    }
                                }
                            }
                        }
                        sink.complete();
                    } catch (Exception e) {
                        if (isTimeoutException(e)) {
                            sink.error(new LlmTimeoutException(provider, "LLM stream timed out", e));
                        } else {
                            sink.error(new LlmServerException(provider, 0, "LLM stream failed: " + e.getMessage()));
                        }
                    }
                }))
                .doOnError(e -> {
                    long elapsed = System.currentTimeMillis() - streamStart;
                    log.warn("Stream error: provider={}, model={}, error={}, chunks={}, elapsed={}ms",
                            provider, model, e.getMessage(), chunkCount[0], elapsed);
                    observation.error(e);
                })
                .doFinally(signalType -> {
                    long elapsed = System.currentTimeMillis() - streamStart;
                    if (signalType == SignalType.ON_COMPLETE) {
                        log.info("Stream completed: provider={}, model={}, chunks={}, elapsed={}ms",
                                provider, model, chunkCount[0], elapsed);
                    } else if (signalType == SignalType.CANCEL) {
                        log.info("Stream cancelled: provider={}, model={}, chunks={}, elapsed={}ms",
                                provider, model, chunkCount[0], elapsed);
                    }
                    observation.stop();
                })
                .retryWhen(RetryUtils.reactorRetrySpec(props.maxRetries()))
                .subscribeOn(Schedulers.boundedElastic()),
                scope -> { /* scope closed by using, observation stopped by doFinally */ }
        );
    }

    // ==================== HTTP 辅助方法 ====================

    /** 构建 GET 请求，自动添加认证 header。 */
    protected HttpRequest buildGetRequest(String path) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(props.baseUrl() + path))
                .timeout(props.timeout())
                .GET();
        configureRequest(builder);
        return builder.build();
    }

    /** 根据 HTTP 状态码和响应体抛出对应的 LlmException。 */
    protected void checkHttpStatus(int statusCode, String body) {
        if (statusCode == 401 || statusCode == 403) {
            throw new LlmAuthException(provider, statusCode,
                    "LLM authentication failed: " + statusCode);
        }
        if (statusCode == 429) {
            throw new LlmRateLimitException(provider, "LLM rate limited (429)");
        }
        if (statusCode >= 500) {
            throw new LlmServerException(provider, statusCode,
                    "LLM server error: " + statusCode);
        }
        if (statusCode >= 400) {
            throw new LlmClientException(provider, statusCode,
                    "LLM client error: " + statusCode);
        }
    }

    // ==================== 请求构建 ====================

    protected OpenAiChatRequest buildRequestBody(ChatRequest request) {
        var messages = request.messages().stream()
                .map(this::toOpenAiMessage)
                .toList();

        List<OpenAiTool> tools = null;
        if (request.tools() != null && !request.tools().isEmpty()) {
            tools = request.tools().stream()
                    .map(t -> OpenAiTool.from(t.name(), t.description(), t.parameters()))
                    .toList();
        }

        ModelOptions opts = request.options();
        String requestModel = (opts != null && opts.model() != null) ? opts.model() : model;

        Map<String, Object> extraBody = (opts != null && !opts.extraBody().isEmpty())
                ? opts.extraBody() : null;

        return new OpenAiChatRequest(
                requestModel,
                messages,
                opts != null ? opts.temperature() : null,
                opts != null ? opts.maxTokens() : null,
                opts != null ? opts.topP() : null,
                opts != null ? opts.stop() : null,
                tools,
                null,
                null,
                null,
                extraBody
        );
    }

    protected OpenAiMessage toOpenAiMessage(Message msg) {
        List<OpenAiToolCall> toolCalls = null;
        if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            toolCalls = msg.toolCalls().stream()
                    .map(tc -> new OpenAiToolCall(
                            tc.id(),
                            "function",
                            new OpenAiToolCall.OpenAiFunctionCall(tc.name(), tc.arguments())))
                    .toList();
        }

        return new OpenAiMessage(
                msg.role(),
                msg.content(),
                msg.name(),
                msg.toolCallId(),
                toolCalls
        );
    }

    // ==================== 响应解析 ====================

    protected ChatResponse parseResponse(@Nullable OpenAiChatResponse response) {
        if (response == null || !response.hasChoices()) {
            return ChatResponse.of("", List.of(), new TokenUsage(0, 0), FinishReason.STOP);
        }

        var choice = response.firstChoice();
        var message = choice.effectiveMessage();
        var finishReason = parseFinishReason(choice.finishReason());

        String content = "";
        List<ToolCall> toolCalls = List.of();

        if (message != null) {
            if (message.content() != null) {
                content = message.content();
            }
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                toolCalls = new ArrayList<>();
                for (OpenAiToolCall tc : message.toolCalls()) {
                    var func = tc.function();
                    if (func != null) {
                        toolCalls.add(new ToolCall(
                                tc.id(),
                                func.name(),
                                func.arguments() != null ? func.arguments() : ""
                        ));
                    }
                }
            }
        }

        return ChatResponse.of(response.id(), response.model(),
                content, toolCalls, extractUsage(response), finishReason, Map.of());
    }

    protected TokenUsage extractUsage(@Nullable OpenAiChatResponse response) {
        if (response == null || response.usage() == null) {
            return new TokenUsage(0, 0);
        }
        var usage = response.usage();
        return new TokenUsage(usage.promptTokens(), usage.completionTokens());
    }

    protected FinishReason parseFinishReason(@Nullable String reason) {
        if (reason == null) return FinishReason.STOP;
        return switch (reason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.LENGTH;
            case "tool_calls" -> FinishReason.TOOL_CALLS;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            default -> {
                log.warn("Unknown finish_reason '{}' from {}, mapping to UNKNOWN", reason, provider);
                yield FinishReason.UNKNOWN;
            }
        };
    }

    protected boolean isTimeoutException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // ==================== 测试辅助方法（package-private） ====================

    ChatResponse parseResponseForTest(OpenAiChatResponse response) {
        return parseResponse(response);
    }

    OpenAiChatRequest buildRequestBodyForTest(ChatRequest request) {
        return buildRequestBody(request);
    }

    FinishReason parseFinishReasonForTest(String reason) {
        return parseFinishReason(reason);
    }
}