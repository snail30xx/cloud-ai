package com.cloudai.llm.exception;

import org.jspecify.annotations.Nullable;

/**
 * LLM 调用异常基类。
 *
 * <p>所有 LLM 相关的异常均继承此类，便于上层统一处理重试、降级和熔断。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public abstract class LlmException extends RuntimeException {

    private final String provider;
    private final int httpStatus;
    private final boolean retryable;

    protected LlmException(String provider, int httpStatus, boolean retryable, String message,
                           @Nullable Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    protected LlmException(String provider, int httpStatus, boolean retryable, String message) {
        this(provider, httpStatus, retryable, message, null);
    }

    /** 提供商名称 */
    public String getProvider() {
        return provider;
    }

    /** HTTP 状态码（0 表示非 HTTP 错误，如连接超时） */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** 是否可重试 */
    public boolean isRetryable() {
        return retryable;
    }
}