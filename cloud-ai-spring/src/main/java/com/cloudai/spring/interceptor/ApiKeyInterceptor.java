package com.cloudai.spring.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 拦截器 — 对 /api/** 路径做 Token 校验。
 *
 * <p>当 cloud-ai.server.api-key 未配置时放行所有请求（开发模式）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ApiKeyInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ApiKeyInterceptor.class);
    private static final String HEADER_NAME = "X-API-Key";

    private final String configuredKey;

    public ApiKeyInterceptor(@Nullable String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (configuredKey == null || configuredKey.isBlank()) {
            return true;
        }
        var requestKey = request.getHeader(HEADER_NAME);
        if (requestKey != null && requestKey.equals(configuredKey)) {
            return true;
        }
        log.warn("API key rejected: method={}, uri={}, remote={}",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":401,\"message\":\"Invalid or missing API key\"}");
        return false;
    }
}