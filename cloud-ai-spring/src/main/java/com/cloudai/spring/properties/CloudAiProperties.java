package com.cloudai.spring.properties;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

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
 * <p><b>绑定约束</b>：同时提供 no-arg 便捷构造器的 record 必须在规范构造器上标注
 * {@link ConstructorBinding}，否则 Spring Boot 在多构造器间无法选择，整个组件绑定结果为
 * null（缺省值全部丢失）。</p>
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
        @Nullable Skill skills,
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
        public record Approval(@Nullable Duration timeout, @Nullable String mode) {}
        public record Permission(@Nullable List<Rule> rules) {
            public record Rule(
                    @Nullable String operation,
                    @Nullable String pattern,
                    boolean allow) {}
        }
    }

    /** 执行配置 — cloud-ai.execution 命名空间。 */
    public record Execution(
            @DefaultValue("true") boolean fileReadEnabled,
            @DefaultValue("true") boolean fileWriteEnabled,
            @DefaultValue("true") boolean shellEnabled,
            @Nullable Duration shellTimeout,
            @Nullable Path workspace) {
        @ConstructorBinding
        public Execution {}

        public Execution() {
            this(true, true, true, null, null);
        }
    }

    /** 记忆配置 — cloud-ai.memory 命名空间。 */
    public record Memory(
            @DefaultValue("8000") int maxContextTokens,
            @DefaultValue("5") int maxRetrievalResults,
            @Nullable String defaultAgentId) {
        @ConstructorBinding
        public Memory {}

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
            @DefaultValue("true") boolean projectContextEnabled) {
        @ConstructorBinding
        public Context {}

        public Context() {
            this(Path.of("."), true);
        }
    }

    /** 技能配置 — cloud-ai.skills 命名空间（组件名 skills，与 enabled 开关键一致）。 */
    public record Skill(
            @Nullable Path workDir,
            @DefaultValue("true") boolean enabled) {
        @ConstructorBinding
        public Skill {}

        public Skill() {
            this(Path.of("."), true);
        }
    }

    /** 运行时配置 — cloud-ai.agent 命名空间。 */
    public record Runtime(
            @DefaultValue("50") int maxTurns,
            @Nullable Duration timeout,
            @Nullable String type,
            @DefaultValue("10") int maxPlanSteps) {
        @ConstructorBinding
        public Runtime {}

        public Runtime() {
            this(50, Duration.ofMinutes(10), "REACT", 10);
        }
    }

    /** 服务器配置 — cloud-ai.server 命名空间。 */
    public record Server(@Nullable String apiKey) {}
}
