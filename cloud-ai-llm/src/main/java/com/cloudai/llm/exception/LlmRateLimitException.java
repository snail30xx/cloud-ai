package com.cloudai.llm.exception;

/**
 * LLM 限流异常（HTTP 429）。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LlmRateLimitException extends LlmException {

    public LlmRateLimitException(String provider, String message) {
        super(provider, 429, true, message);
    }
}