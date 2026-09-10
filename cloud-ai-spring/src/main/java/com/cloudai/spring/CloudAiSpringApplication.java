package com.cloudai.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动入口 — 使用 cloud-ai-spring 自动装配集成。
 *
 * <p>在 application.properties 中配置 cloud-ai.llm.providers.* 等参数后，
 * 直接运行此类即可启动完整 Agent HTTP 服务。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@SpringBootApplication(scanBasePackages = {"com.cloudai.spring", "com.cloudai.server"})
public class CloudAiSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudAiSpringApplication.class, args);
    }
}