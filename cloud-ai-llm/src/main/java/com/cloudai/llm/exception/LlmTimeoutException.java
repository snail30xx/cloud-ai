package com.cloudai.llm.exception;

/**
 * LLM 调用超时异常（连接超时或读取超时）。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String provider, String message, Throwable cause) {
        super(provider, 0, true, message, cause);
    }
}