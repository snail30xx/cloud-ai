package com.cloudai.llm.adapter;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.ChatResponse;
import com.cloudai.core.model.FinishReason;
import com.cloudai.core.model.Message;
import com.cloudai.core.model.ModelInfo;
import com.cloudai.core.model.TokenUsage;
import com.cloudai.core.spi.ChatModel;
import com.cloudai.core.spi.ModelDiscovery;
import com.cloudai.llm.interceptor.LoggingClientHttpRequestInterceptor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 适配器（维信内部路由）。
 *
 * <p>适配 Anthropic 格式的 LLM API，接入标准 {@link ChatModel} + {@link ChatClient} 流程。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class VcreditAnthropicAdapter implements ChatModel, ModelDiscovery {
    private static final Logger log = LoggerFactory.getLogger(VcreditAnthropicAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String provider;
    private final String model;
    private final int maxTokens;

    public VcreditAnthropicAdapter(String provider, String model, String baseUrl, String apiKey,
                                   int maxTokens, Duration timeout) {
        this.provider = provider;
        this.model = model;
        this.maxTokens = maxTokens;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(factory)
                .requestInterceptor(new LoggingClientHttpRequestInterceptor())
                .build();
    }

    // ==================== ChatModel ====================

    @Override
    public ChatResponse call(ChatRequest request) {
        var body = buildRequestBody(request);
        var response = restClient.post()
                .uri("/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(AnthropicResponse.class);

        return parseResponse(response);
    }

    @Override
    public Flux<ChatResponse> stream(ChatRequest request) {
        var body = buildRequestBody(request);
        body.put("stream", true);

        return Flux.create(sink -> {
            try {
                restClient.post()
                        .uri("/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .exchange((req, resp) -> {
                            try (var reader = new BufferedReader(
                                    new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data: ")) {
                                        String data = line.substring(6).trim();
                                        try {
                                            var chunk = MAPPER.readValue(data, AnthropicStreamEvent.class);
                                            if ("content_block_delta".equals(chunk.type)
                                                    && chunk.delta != null
                                                    && "text_delta".equals(chunk.delta.type)) {
                                                sink.next(ChatResponse.of(
                                                        chunk.delta.text, List.of(),
                                                        new TokenUsage(0, 0), FinishReason.STOP));
                                            } else if ("message_stop".equals(chunk.type)) {
                                                sink.complete();
                                            }
                                        } catch (Exception e) {
                                            log.debug("Failed to parse SSE: {}", data, e);
                                        }
                                    }
                                }
                            }
                            sink.complete();
                            return null;
                        });
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    // ==================== ModelDiscovery ====================

    @Override
    public ModelInfo getModelInfo() {
        return new ModelInfo(provider, model, maxTokens, List.of("chat"));
    }

    @Override
    public List<ModelInfo> listModels() {
        return List.of(getModelInfo());
    }

    // ==================== 内部 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(ChatRequest request) {
        var messages = new ArrayList<Map<String, String>>();
        for (var msg : request.messages()) {
            messages.add(Map.of("role", msg.role(), "content",
                    msg.content() != null ? msg.content() : ""));
        }

        int maxTok = request.options() != null && request.options().maxTokens() != null
                ? request.options().maxTokens() : maxTokens;

        return (Map<String, Object>) (Map) Map.of(
                "model", model,
                "max_tokens", maxTok,
                "messages", messages
        );
    }

    private ChatResponse parseResponse(AnthropicResponse resp) {
        if (resp == null) {
            return ChatResponse.of("", List.of(), new TokenUsage(0, 0), FinishReason.STOP);
        }
        var text = new StringBuilder();
        if (resp.content != null) {
            for (var block : resp.content) {
                if (block.text != null) {
                    text.append(block.text);
                }
            }
        }
        var usage = resp.usage != null
                ? new TokenUsage(resp.usage.inputTokens, resp.usage.outputTokens)
                : new TokenUsage(0, 0);
        var finishReason = "end_turn".equals(resp.stopReason) ? FinishReason.STOP
                : "max_tokens".equals(resp.stopReason) ? FinishReason.LENGTH
                : FinishReason.STOP;

        return ChatResponse.of(resp.id, resp.model, text.toString(), List.of(), usage, finishReason, Map.of());
    }

    // ==================== 响应模型 ====================

    record AnthropicResponse(
            String id, String model, String role,
            @JsonProperty("stop_reason") String stopReason,
            List<ContentBlock> content,
            Usage usage
    ) {}

    record ContentBlock(String type, String text) {}

    record Usage(@JsonProperty("input_tokens") int inputTokens,
                 @JsonProperty("output_tokens") int outputTokens) {}

    record AnthropicStreamEvent(String type, Delta delta) {}

    record Delta(String type, String text) {}
}