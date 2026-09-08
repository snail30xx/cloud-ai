package com.cloudai.llm.exception;

/**
 * LLM 鉴权失败异常（HTTP 401/403）。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LlmAuthException extends LlmException {

    public LlmAuthException(String provider, int httpStatus, String message) {
        super(provider, httpStatus, false, message);
    }
}