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
 * RestClient 日志拦截器 — 记录每次 HTTP 请求和响应的完整 JSON。
 *
 * <p>INFO 级别输出请求/响应的完整 JSON body。</p>
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
        String reqBody = body != null && body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "(empty)";
        log.info("[HTTP Request] {} {}\n{}", request.getMethod(), request.getURI(), prettyJson(reqBody));

        long start = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long elapsed = System.currentTimeMillis() - start;

        byte[] respBytes = response.getBody().readAllBytes();
        String respBody = respBytes.length > 0 ? new String(respBytes, StandardCharsets.UTF_8) : "(empty)";

        log.info("[HTTP Response] {} -> {} ({}ms)\n{}",
                request.getURI(), response.getStatusCode(), elapsed, prettyJson(respBody));

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
