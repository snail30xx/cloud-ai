package com.cloudai.llm.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestClient 日志拦截器 — 记录每次 HTTP 请求的方法、URI、状态码和耗时。
 *
 * <p>INFO 级别输出请求/响应摘要，DEBUG 级别额外输出请求体大小。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LoggingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingClientHttpRequestInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.currentTimeMillis();

        if (log.isDebugEnabled()) {
            log.debug("HTTP {} {} (body={} bytes)", request.getMethod(), request.getURI(),
                    body != null ? body.length : 0);
        } else {
            log.info("HTTP {} {}", request.getMethod(), request.getURI());
        }

        ClientHttpResponse response = execution.execute(request, body);

        long elapsed = System.currentTimeMillis() - start;
        log.info("HTTP {} {} -> {} ({}ms)",
                request.getMethod(), request.getURI(), response.getStatusCode(), elapsed);

        return response;
    }
}