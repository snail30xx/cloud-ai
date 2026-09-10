package com.cloudai.llm.client.support;

import com.cloudai.core.chat.ChatResponse;
import com.cloudai.llm.client.spec.CallResponseSpec;
import org.jspecify.annotations.Nullable;

/**
 * {@link CallResponseSpec} 默认实现。
 *
 * @author cloud-ai
 * @since 1.0
 */
public record DefaultCallResponseSpec(@Nullable ChatResponse response) implements CallResponseSpec {
    @Override
    public String content() {
        return response != null ? response.content() : "";
    }

    @Override
    @Nullable
    public ChatResponse chatResponse() {
        return response;
    }
}