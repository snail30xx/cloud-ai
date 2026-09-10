package com.cloudai.llm.client.spec;

import com.cloudai.core.chat.ChatResponse;
import org.jspecify.annotations.Nullable;

/**
 * 同步调用响应规格。
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface CallResponseSpec {

    /** 获取响应文本内容 */
    String content();

    /** 获取完整 ChatResponse */
    @Nullable
    ChatResponse chatResponse();
}