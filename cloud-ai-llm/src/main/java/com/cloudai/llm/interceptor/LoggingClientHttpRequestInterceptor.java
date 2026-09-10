package com.cloudai.llm.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * RestClient 日志拦截器 — 记录每次 HTTP 请求和响应的摘要信息。
 *
 * <p>INFO 级别输出请求/响应摘要（方法、URI、状态码、耗时）。
 * DEBUG 级别输出完整 JSON body（含敏感对话内容，生产环境慎用）。
 * SSE 流式响应不缓冲 body，保持真实流式语义。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LoggingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingClientHttpRequestInterceptor.class);
    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        log.info("[HTTP Request] {} {}", request.getMethod(), request.getURI());
        if (log.isDebugEnabled()) {
            String reqBody = body != null && body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "(empty)";
            log.debug("[HTTP Request Body]\n{}", prettyJson(reqBody));
        }

        long start = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long elapsed = System.currentTimeMillis() - start;

        // SSE 流式响应不缓冲 body，保持真实流式语义
        String contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType().toString() : "";
        if (contentType.contains("text/event-stream")) {
            log.info("[HTTP Response] {} -> {} ({}ms) [SSE, not buffered]",
                    request.getURI(), response.getStatusCode(), elapsed);
            return response;
        }

        // 非流式响应：缓冲 body 以支持重复读取和日志记录
        byte[] respBytes = response.getBody().readAllBytes();
        log.info("[HTTP Response] {} -> {} ({}ms)", request.getURI(), response.getStatusCode(), elapsed);
        if (log.isDebugEnabled()) {
            String respBody = respBytes.length > 0 ? new String(respBytes, StandardCharsets.UTF_8) : "(empty)";
            log.debug("[HTTP Response Body]\n{}", prettyJson(respBody));
        }
        return new BufferedClientHttpResponse(response, respBytes);
    }

    private static String prettyJson(String json) {
        if (json == null || json.isBlank() || json.startsWith("(")) {
            return json;
        }
        try {
            Object parsed = PRETTY.readValue(json, Object.class);
            return PRETTY.writeValueAsString(parsed);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * 包装 ClientHttpResponse，使 getBody() 可重复读取。
     */
    private static class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
