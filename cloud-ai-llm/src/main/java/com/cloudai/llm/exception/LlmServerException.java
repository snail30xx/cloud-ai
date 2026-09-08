package com.cloudai.llm.exception;

/**
 * LLM 服务端错误异常（HTTP 5xx）。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LlmServerException extends LlmException {

    public LlmServerException(String provider, int httpStatus, String message) {
        super(provider, httpStatus, true, message);
    }
}