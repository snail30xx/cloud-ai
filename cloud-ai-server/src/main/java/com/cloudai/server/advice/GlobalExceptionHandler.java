package com.cloudai.server.advice;

import com.cloudai.llm.exception.LlmAuthException;
import com.cloudai.llm.exception.LlmClientException;
import com.cloudai.llm.exception.LlmException;
import com.cloudai.llm.exception.LlmRateLimitException;
import com.cloudai.llm.exception.LlmServerException;
import com.cloudai.llm.exception.LlmTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 全局异常处理器 — 将 LLM 异常映射为 HTTP 响应，不泄露内部堆栈。
 *
 * @author cloud-ai
 * @since 1.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(LlmAuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(LlmAuthException e) {
        log.warn("LLM auth error: provider={}, status={}", e.getProvider(), e.getHttpStatus());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody(401, "Authentication failed"));
    }

    @ExceptionHandler(LlmRateLimitException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(LlmRateLimitException e) {
        log.warn("LLM rate limited: provider={}", e.getProvider());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(errorBody(429, "Rate limited by provider"));
    }

    @ExceptionHandler(LlmTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(LlmTimeoutException e) {
        log.warn("LLM timeout: provider={}", e.getProvider());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(errorBody(504, "Request timed out"));
    }

    @ExceptionHandler(LlmServerException.class)
    public ResponseEntity<Map<String, Object>> handleServer(LlmServerException e) {
        log.error("LLM server error: provider={}, status={}", e.getProvider(), e.getHttpStatus(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorBody(502, "Provider server error"));
    }

    @ExceptionHandler(LlmClientException.class)
    public ResponseEntity<Map<String, Object>> handleClient(LlmClientException e) {
        log.warn("LLM client error: provider={}, status={}", e.getProvider(), e.getHttpStatus());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(400, "Invalid request to provider"));
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<Map<String, Object>> handleLlm(LlmException e) {
        log.error("Unhandled LLM error: provider={}", e.getProvider(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(500, "LLM error"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(400, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(500, "Internal error"));
    }

    private static Map<String, Object> errorBody(int code, String message) {
        return Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "code", code,
                "message", message
        );
    }
}
