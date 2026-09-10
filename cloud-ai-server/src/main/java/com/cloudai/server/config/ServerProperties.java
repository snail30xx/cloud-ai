package com.cloudai.server.config;

import org.jspecify.annotations.Nullable;

/**
 * Server 配置属性 — 绑定 {@code cloud-ai.server} 命名空间。
 *
 * @param apiKey API Key，未配置时放行所有请求（开发模式）
 * @author cloud-ai
 * @since 1.0
 */
public record ServerProperties(@Nullable String apiKey) {
}
