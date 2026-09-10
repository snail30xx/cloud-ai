package com.cloudai.llm.observation;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import io.micrometer.observation.Observation;
import org.jspecify.annotations.Nullable;

/** LLM 聊天调用观测上下文。
 *
 * <p>持有单次 LLM 调用的关键信息，供 {@link DefaultChatModelObservationConvention}
 * 提取为 OpenTelemetry KeyValue 标签。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ChatModelObservationContext extends Observation.Context {

    private final String provider;
    private final String model;
    private final boolean streaming;
    private final int messageCount;
    /** 响应结果，Observation 未完成前为 null */
    private @Nullable ChatResponse response;

    public ChatModelObservationContext(String provider, String model, ChatRequest request, boolean streaming) {
        this.provider = provider;
        this.model = model;
        this.streaming = streaming;
        this.messageCount = request.messages() != null ? request.messages().size() : 0;
    }

    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public boolean isStreaming() { return streaming; }
    public int getMessageCount() { return messageCount; }

    /** 获取响应结果，Observation 未完成前返回 null */
    @Nullable
    public ChatResponse getResponse() { return response; }
    /** 设置响应结果（Observation 完成后调用） */
    public void setResponse(ChatResponse response) { this.response = response; }
}