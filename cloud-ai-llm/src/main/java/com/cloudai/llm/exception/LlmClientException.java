package com.cloudai.llm.exception;

/**
 * LLM 客户端错误异常（4xx，非 401/403/429）。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LlmClientException extends LlmException {

    public LlmClientException(String provider, int httpStatus, String message) {
        super(provider, httpStatus, false, message);
    }
}