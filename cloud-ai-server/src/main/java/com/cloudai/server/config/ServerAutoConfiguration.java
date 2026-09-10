package com.cloudai.server.config;

import com.cloudai.server.interceptor.ApiKeyInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Server 自动配置 — 注册 API Key 拦截器。
 *
 * <p>通过 {@code cloud-ai.server.api-key} 控制。未配置时放行所有请求（开发模式）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(ServerProperties.class)
@ConditionalOnProperty(name = "cloud-ai.server.enabled", havingValue = "true", matchIfMissing = true)
public class ServerAutoConfiguration implements WebMvcConfigurer {

    private final ServerProperties properties;

    public ServerAutoConfiguration(ServerProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyInterceptor apiKeyInterceptor() {
        return new ApiKeyInterceptor(properties.apiKey());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor())
                .addPathPatterns("/api/**");
    }
}
