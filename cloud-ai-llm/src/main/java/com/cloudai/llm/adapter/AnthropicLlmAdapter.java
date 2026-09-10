package com.cloudai.llm.adapter;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.ModelInfo;
import com.cloudai.core.chat.TokenUsage;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.llm.observation.ChatModelObservationContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import java.net.http.HttpRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 适配器。
 *
 * <p>继承 {@link AbstractLlmAdapter}，复用框架的观测、重试、异常映射、
 * HTTP 日志等基础设施，仅覆盖 Anthropic 协议特有的部分：</p>
 * <ul>
 *   <li>认证：{@code x-api-key} + {@code anthropic-version} 头</li>
 *   <li>端点：{@code /messages}（而非 {@code /chat/completions}）</li>
 *   <li>请求/响应格式：Anthropic Messages API 格式</li>
 *   <li>SSE 事件：{@code content_block_delta} / {@code message_stop}</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class AnthropicLlmAdapter extends AbstractLlmAdapter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AnthropicLlmAdapter(ProviderProperties props) {
        this(props, ObservationRegistry.NOOP, null);
    }

    public AnthropicLlmAdapter(ProviderProperties props,
                               @Nullable ObservationRegistry observationRegistry) {
        this(props, observationRegistry, null);
    }

    public AnthropicLlmAdapter(ProviderProperties props,
                               @Nullable ObservationRegistry observationRegistry,
                               @Nullable ObservationConvention<ChatModelObservationContext> convention) {
        super(props, "anthropic", observationRegistry, convention);
    }

    // ==================== 模板方法：认证 ====================

    @Override
    protected void configureRequest(HttpRequest.Builder builder) {
        builder.header("x-api-key", props.apiKey());
        builder.header("anthropic-version", "2023-06-01");
    }

    // ==================== 模板方法：端点 + 响应类型 ====================

    @Override
    protected String getChatEndpoint() {
        return "/messages";
    }

    @Override
    protected Class<?> getChatResponseType() {
        return AnthropicResponse.class;
    }

    // ==================== 模板方法：请求构建 ====================

    @Override
    protected Object buildRequestBodyInternal(ChatRequest request) {
        var messages = new ArrayList<Map<String, Object>>();
        for (var msg : request.messages()) {
            var content = new LinkedHashMap<String, Object>();
            content.put("type", "text");
            content.put("text", msg.content() != null ? msg.content() : "");
            messages.add(Map.of("role", msg.role(), "content", List.of(content)));
        }

        int maxTok = request.options() != null && request.options().maxTokens() != null
                ? request.options().maxTokens()
                : props.maxContextTokens();

        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("max_tokens", maxTok);
        body.put("messages", messages);

        return body;
    }

    @Override
    protected Object setStreamFlag(Object body) {
        @SuppressWarnings("unchecked")
        var map = (Map<String, Object>) body;
        map.put("stream", true);
        return map;
    }

    // ==================== 模板方法：响应解析 ====================

    @Override
    protected ChatResponse parseResponseInternal(Object rawResponse) {
        if (rawResponse instanceof AnthropicResponse resp) {
            return parseAnthropicResponse(resp);
        }
        return ChatResponse.of("", List.of(), new TokenUsage(0, 0), FinishReason.STOP);
    }

    @Override
    @Nullable
    protected ChatResponse parseSseLineInternal(String data) throws Exception {
        var event = MAPPER.readValue(data, AnthropicStreamEvent.class);
        if ("content_block_delta".equals(event.type)
                && event.delta != null
                && "text_delta".equals(event.delta.type)
                && event.delta.text != null) {
            return ChatResponse.of(event.delta.text, List.of(),
                    new TokenUsage(0, 0), FinishReason.STOP);
        }
        if ("message_stop".equals(event.type)) {
            // stream 结束信号，不产生 chunk
            return null;
        }
        return null; // 跳过 ping 等其他事件
    }

    // ==================== ModelDiscovery（直接返回配置，不调 API） ====================

    @Override
    public ModelInfo getModelInfo() {
        return new ModelInfo(provider, model, props.maxContextTokens(), props.capabilities());
    }

    @Override
    public List<ModelInfo> listModels() {
        return List.of(getModelInfo());
    }

    // ==================== 内部 ====================

    private ChatResponse parseAnthropicResponse(AnthropicResponse resp) {
        var text = new StringBuilder();
        if (resp.content != null) {
            for (var block : resp.content) {
                if ("text".equals(block.type) && block.text != null) {
                    text.append(block.text);
                }
            }
        }
        var usage = resp.usage != null
                ? new TokenUsage(resp.usage.inputTokens, resp.usage.outputTokens)
                : new TokenUsage(0, 0);
        var finishReason = "end_turn".equals(resp.stopReason) ? FinishReason.STOP
                : "max_tokens".equals(resp.stopReason) ? FinishReason.LENGTH
                : "tool_use".equals(resp.stopReason) ? FinishReason.TOOL_CALLS
                : FinishReason.STOP;

        return ChatResponse.of(resp.id, resp.model, text.toString(),
                List.of(), usage, finishReason, Map.of());
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
