package com.cloudai.spring.config;

import com.cloudai.llm.ModelRouter;
import com.cloudai.spring.properties.CloudAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

/**
 * cloud-ai Spring Boot 自动装配入口。
 *
 * <p>通过 cloud-ai.enabled 控制（默认 true）。
 * 装配顺序：安全 -> 记忆 -> 人格 -> 上下文 -> 技能 -> 执行 -> LLM -> 运行时 -> Server。</p>
 *
 * <p>所有 @Bean 均标注 @ConditionalOnMissingBean，允许外部覆盖。
 * 各模块的工厂方法（纯 Java 静态方法）在此被 Spring DI 调用。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@AutoConfiguration
@ConditionalOnProperty(name = "cloud-ai.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(ModelRouter.class)
@EnableConfigurationProperties(CloudAiProperties.class)
@Import({
        LlmAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        MemoryAutoConfiguration.class,
        PersonaAutoConfiguration.class,
        ContextAutoConfiguration.class,
        SkillsAutoConfiguration.class,
        ExecutionAutoConfiguration.class,
        RuntimeAutoConfiguration.class,
        ServerAutoConfiguration.class
})
public class CloudAiAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(CloudAiAutoConfiguration.class);

    @Bean
    public Path workDir(Environment env) {
        var dir = env.getProperty("cloud-ai.context.work-dir", ".");
        return Path.of(dir);
    }
}