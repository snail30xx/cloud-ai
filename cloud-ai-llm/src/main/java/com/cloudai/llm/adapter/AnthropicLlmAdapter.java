package com.cloudai.llm.adapter;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.Message;
import com.cloudai.core.chat.ModelInfo;
import com.cloudai.core.chat.TokenUsage;
import com.cloudai.core.tool.ToolCall;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.llm.observation.ChatModelObservationContext;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>system 消息映射为请求体顶层 {@code system} 字段（非消息数组元素）</li>
 *   <li>工具定义映射为 {@code tools[].input_schema}；
 *       assistant 的 {@code ToolCall} 映射为 {@code tool_use} 内容块；
 *       tool 结果消息映射为 user 角色的 {@code tool_result} 内容块</li>
 *   <li>响应中的 {@code tool_use} 内容块解析回 {@link ToolCall}</li>
 *   <li>SSE 事件：{@code content_block_delta}（text_delta）/ {@code message_stop}</li>
 * </ul>
 *
 * <p><b>流式限制</b>：当前流式仅透传文本增量（text_delta），
 * tool_use 的流式增量聚合（input_json_delta）尚未支持，
 * 需要工具调用的场景请使用同步 {@code call()}。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class AnthropicLlmAdapter extends AbstractLlmAdapter {
    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmAdapter.class);
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
        var systemText = new StringBuilder();
        var messages = new ArrayList<Map<String, Object>>();
        for (var msg : request.messages()) {
            if (msg.isSystem()) {
                appendSystem(systemText, msg);
            } else if (msg.isTool()) {
                messages.add(toolResultMessage(msg));
            } else {
                messages.add(chatMessage(msg));
            }
        }

        int maxTok = request.options() != null && request.options().maxTokens() != null
                ? request.options().maxTokens()
                : props.maxContextTokens();

        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("max_tokens", maxTok);
        if (systemText.length() > 0) {
            body.put("system", systemText.toString());
        }
        body.put("messages", messages);
        var tools = toolSchemas(request);
        if (!tools.isEmpty()) {
            // Anthropic 拒绝空 tools 数组，仅在存在工具定义时发送
            body.put("tools", tools);
        }

        if (request.options() != null) {
            if (request.options().temperature() != null) {
                body.put("temperature", request.options().temperature());
            }
            if (request.options().topP() != null) {
                body.put("top_p", request.options().topP());
            }
            if (request.options().stop() != null && !request.options().stop().isEmpty()) {
                body.put("stop_sequences", request.options().stop());
            }
        }

        return body;
    }

    private static void appendSystem(StringBuilder systemText, Message msg) {
        if (msg.content() == null || msg.content().isBlank()) {
            return;
        }
        if (systemText.length() > 0) {
            systemText.append("\n\n");
        }
        systemText.append(msg.content());
    }

    /** tool 结果消息 → user 角色 tool_result 内容块（Anthropic 无 tool 角色）。 */
    private static Map<String, Object> toolResultMessage(Message msg) {
        return Map.of("role", "user", "content", List.of(Map.of(
                "type", "tool_result",
                "tool_use_id", msg.toolCallId() != null ? msg.toolCallId() : "",
                "content", msg.content() != null ? msg.content() : "")));
    }

    /** user / assistant 消息 → 内容块列表（文本 + 可选 tool_use）。 */
    private static Map<String, Object> chatMessage(Message msg) {
        var blocks = new ArrayList<Map<String, Object>>();
        if (msg.content() != null && !msg.content().isBlank()) {
            blocks.add(Map.of("type", "text", "text", msg.content()));
        }
        if (msg.isAssistant() && !msg.toolCalls().isEmpty()) {
            for (var tc : msg.toolCalls()) {
                blocks.add(Map.of(
                        "type", "tool_use",
                        "id", tc.id() != null ? tc.id() : "",
                        "name", tc.name() != null ? tc.name() : "",
                        "input", parseToolInput(tc.arguments())));
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(Map.of("type", "text", "text", ""));
        }
        return Map.of("role", msg.isAssistant() ? "assistant" : "user", "content", blocks);
    }

    /** 工具参数 JSON 字符串 → Anthropic input 对象；无法解析时降级为空对象并告警。 */
    private static Map<String, Object> parseToolInput(@Nullable String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Tool call arguments are not a JSON object, sending empty input: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 统一工具定义 → Anthropic tools[]（input_schema 即 JSON Schema）。 */
    private static List<Map<String, Object>> toolSchemas(ChatRequest request) {
        if (request.tools() == null || request.tools().isEmpty()) {
            return List.of();
        }
        var tools = new ArrayList<Map<String, Object>>();
        for (var t : request.tools()) {
            tools.add(Map.of(
                    "name", t.name(),
                    "description", t.description() != null ? t.description() : "",
                    "input_schema", t.parameters() != null ? t.parameters() : Map.of("type", "object")));
        }
        return tools;
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
        var toolCalls = new ArrayList<ToolCall>();
        if (resp.content != null) {
            for (var block : resp.content) {
                if ("text".equals(block.type) && block.text != null) {
                    text.append(block.text);
                } else if ("tool_use".equals(block.type) && block.id != null && block.name != null) {
                    toolCalls.add(new ToolCall(block.id, block.name,
                            block.input != null ? writeToolInput(block.input) : "{}"));
                }
            }
        }
        var usage = resp.usage != null
                ? new TokenUsage(resp.usage.inputTokens, resp.usage.outputTokens)
                : new TokenUsage(0, 0);
        var finishReason = parseStopReason(resp.stopReason);

        return ChatResponse.of(resp.id, resp.model, text.toString(),
                List.copyOf(toolCalls), usage, finishReason, Map.of());
    }

    private static String writeToolInput(Map<String, Object> input) {
        try {
            return MAPPER.writeValueAsString(input);
        } catch (Exception e) {
            log.warn("Failed to serialize tool_use input, sending empty object: {}", e.getMessage());
            return "{}";
        }
    }

    private static FinishReason parseStopReason(@Nullable String stopReason) {
        if (stopReason == null) {
            return FinishReason.STOP;
        }
        return switch (stopReason) {
            case "max_tokens" -> FinishReason.LENGTH;
            case "tool_use" -> FinishReason.TOOL_CALLS;
            default -> FinishReason.STOP; // end_turn / stop_sequence
        };
    }

    // ==================== 响应模型 ====================

    record AnthropicResponse(
            String id, String model, String role,
            @JsonProperty("stop_reason") String stopReason,
            List<ContentBlock> content,
            Usage usage
    ) {}

    record ContentBlock(String type, String text, String id, String name, Map<String, Object> input) {}

    record Usage(@JsonProperty("input_tokens") int inputTokens,
                 @JsonProperty("output_tokens") int outputTokens) {}

    record AnthropicStreamEvent(String type, Delta delta) {}

    record Delta(String type, String text) {}
}
