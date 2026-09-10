package com.cloudai.spring.properties;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * cloud-ai 全模块 Spring Boot 配置属性 — 绑定 {@code cloud-ai.*} 命名空间。
 *
 * <p>各内部 record 对应一个模块的配置子命名空间，由 Spring Boot 松散绑定。
 * 在 {@link com.cloudai.spring.config.CloudAiAutoConfiguration} 的各子配置类中
 * 转换为框架纯 Java Properties record 后调用各模块工厂方法。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@ConfigurationProperties("cloud-ai")
public record CloudAiProperties(
        @Nullable Llm llm,
        @Nullable Security security,
        @Nullable Execution execution,
        @Nullable Memory memory,
        @Nullable Persona persona,
        @Nullable Context context,
        @Nullable Skill skill,
        @Nullable Runtime runtime,
        @Nullable Server server) {

    /** LLM 配置 — cloud-ai.llm 命名空间。 */
    public record Llm(
            @Nullable String defaultProvider,
            @Nullable Map<String, Provider> providers) {
        public record Provider(
                @Nullable String baseUrl,
                @Nullable String apiKey,
                @Nullable String model,
                @Nullable Duration timeout,
                @Nullable Integer maxContextTokens,
                @Nullable Integer maxRetries,
                @Nullable List<String> capabilities) {}
    }

    /** 安全配置 — cloud-ai.security 命名空间。 */
    public record Security(
            @Nullable Approval approval,
            @Nullable Permission permission) {
        public record Approval(@Nullable Duration timeout) {}
        public record Permission(@Nullable List<Rule> rules) {
            public record Rule(
                    @Nullable String operation,
                    @Nullable String pattern,
                    boolean allow) {}
        }
    }

    /** 执行配置 — cloud-ai.execution 命名空间。 */
    public record Execution(
            boolean fileReadEnabled,
            boolean fileWriteEnabled,
            boolean fileDeleteEnabled,
            boolean shellEnabled) {
        public Execution() {
            this(true, true, true, true);
        }
    }

    /** 记忆配置 — cloud-ai.memory 命名空间。 */
    public record Memory(
            int maxContextTokens,
            int maxRetrievalResults,
            @Nullable String defaultAgentId) {
        public Memory() {
            this(8000, 5, "cloud-ai-agent");
        }
    }

    /** 人格配置 — cloud-ai.persona 命名空间。 */
    public record Persona(
            @Nullable String defaultPersonaId,
            @Nullable Map<String, PersonaConfig> personas) {
        public record PersonaConfig(
                @Nullable String name,
                @Nullable String role,
                @Nullable String systemPrompt,
                @Nullable List<String> guidelines,
                @Nullable String toneStyle,
                @Nullable List<String> constraints) {}
    }

    /** 上下文配置 — cloud-ai.context 命名空间。 */
    public record Context(
            @Nullable Path workDir,
            boolean projectContextEnabled) {
        public Context() {
            this(Path.of("."), true);
        }
    }

    /** 技能配置 — cloud-ai.skills 命名空间。 */
    public record Skill(
            @Nullable Path workDir,
            boolean enabled) {
        public Skill() {
            this(Path.of("."), true);
        }
    }

    /** 运行时配置 — cloud-ai.agent 命名空间。 */
    public record Runtime(
            int maxTurns,
            @Nullable Duration timeout,
            @Nullable String type,
            int maxPlanSteps) {
        public Runtime() {
            this(50, Duration.ofMinutes(10), "REACT", 10);
        }
    }

    /** 服务器配置 — cloud-ai.server 命名空间。 */
    public record Server(@Nullable String apiKey) {}
}