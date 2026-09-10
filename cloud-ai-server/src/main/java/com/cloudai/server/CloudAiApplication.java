package com.cloudai.server;

import com.cloudai.core.chat.ChatModel;
import com.cloudai.core.chat.ContextManager;
import com.cloudai.execution.config.ExecutionProperties;
import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.llm.config.LlmProperties;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.llm.ModelRouter;
import com.cloudai.memory.InMemoryMemoryStore;
import com.cloudai.memory.KeywordMemoryRetriever;
import com.cloudai.memory.SimpleContextManager;
import com.cloudai.memory.MemoryRetriever;
import com.cloudai.memory.MemoryStore;
import com.cloudai.persona.DefaultPersonaAssembler;
import com.cloudai.persona.DefaultPersonaProvider;
import com.cloudai.persona.PersonaAssembler;
import com.cloudai.persona.PersonaProvider;
import com.cloudai.persona.config.PersonaProperties;
import com.cloudai.runtime.config.RuntimeProperties;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.runtime.AgentType;
import com.cloudai.security.config.SecurityProperties;
import com.cloudai.security.SecurityInterceptor;
import com.cloudai.security.permission.DefaultPermissionManager;
import com.cloudai.security.permission.OperationType;
import com.cloudai.server.controller.AgentHttpHandler;
import com.cloudai.server.service.AgentService;
import com.cloudai.skills.LoadSkillExecutor;
import com.cloudai.skills.SkillRegistry;
import com.cloudai.skills.config.SkillAutoConfiguration;
import com.cloudai.skills.config.SkillProperties;
import com.cloudai.execution.config.ExecutionAutoConfiguration;
import com.cloudai.memory.config.MemoryAutoConfiguration;
import com.cloudai.llm.config.LlmAutoConfiguration;
import com.cloudai.security.config.SecurityAutoConfiguration;
import com.cloudai.runtime.config.RuntimeAutoConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;

/**
 * Cloud AI 独立服务器启动入口 — 纯 JDK 装配，无 Spring。
 *
 * <p>加载 {@code cloud-ai-server.properties}（classpath，可被外部
 * {@code ./cloud-ai-server.properties} 覆盖），通过各模块静态工厂手动装配
 * {@link AgentService}，并启动 JDK 内置 {@link HttpServer} 暴露 HTTP API。</p>
 *
 * <p>配置文件刻意避开 {@code application.*} 命名：依赖本模块的 Spring Boot 应用
 * 不会误读它（Spring Boot 会自动加载 classpath 上的 application.*）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class CloudAiApplication {
    private static final Logger log = LoggerFactory.getLogger(CloudAiApplication.class);

    private static final String CONFIG_RESOURCE = "cloud-ai-server.properties";
    private static final String EXTERNAL_CONFIG = "cloud-ai-server.properties";
    private static final String LLM_PROVIDER_PREFIX = "cloud-ai.llm.providers.";

    private CloudAiApplication() {}

    public static void main(String[] args) throws Exception {
        var props = loadProperties();

        int port = intProperty(props, "server.port", 8080);
        String apiKey = emptyToNull(props.getProperty("cloud-ai.server.api-key"));

        var agentService = assemble(props, null);
        var server = startServer(port, agentService, apiKey);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Cloud AI HTTP server");
            server.stop(2);
        }));

        // 虚拟线程为 daemon，主线程需阻塞以维持进程
        new CountDownLatch(1).await();
    }

    /**
     * 从 classpath 与外部文件加载配置并解析 {@code ${ENV:default}} 占位符。
     * 外部文件（工作目录下同名文件）优先于 classpath 版本。
     */
    static Properties loadProperties() {
        var props = new Properties();
        try (InputStream is = CloudAiApplication.class.getClassLoader()
                .getResourceAsStream(CONFIG_RESOURCE)) {
            if (is != null) {
                props.load(is);
                log.info("Loaded {} from classpath", CONFIG_RESOURCE);
            } else {
                log.warn("{} not found on classpath, using built-in defaults", CONFIG_RESOURCE);
            }
        } catch (IOException e) {
            log.warn("Failed to load {}: {}", CONFIG_RESOURCE, e.getMessage());
        }

        var external = Path.of(EXTERNAL_CONFIG).toFile();
        if (external.isFile()) {
            try (InputStream is = external.toURI().toURL().openStream()) {
                props.load(is);
                log.info("Loaded external {} (overrides classpath values)", EXTERNAL_CONFIG);
            } catch (IOException e) {
                log.warn("Failed to load external {}: {}", EXTERNAL_CONFIG, e.getMessage());
            }
        }

        var resolved = new Properties();
        for (var entry : props.entrySet()) {
            resolved.setProperty(String.valueOf(entry.getKey()),
                    resolvePlaceholders(String.valueOf(entry.getValue())));
        }
        return resolved;
    }

    /**
     * 解析 {@code ${ENV_VAR:default}} 占位符：环境变量存在且非空用其值，否则用默认值；
     * 无默认值则保留空串。不支持嵌套占位符。
     */
    static String resolvePlaceholders(String value) {
        if (!value.contains("${")) {
            return value;
        }
        var result = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("${", i);
            if (start < 0) {
                result.append(value, i, value.length());
                break;
            }
            result.append(value, i, start);
            int end = value.indexOf('}', start);
            if (end < 0) {
                result.append(value.substring(start));
                break;
            }
            var expr = value.substring(start + 2, end);
            int colon = expr.indexOf(':');
            String envValue;
            String fallback;
            if (colon >= 0) {
                envValue = System.getenv(expr.substring(0, colon));
                fallback = expr.substring(colon + 1);
            } else {
                envValue = System.getenv(expr);
                fallback = "";
            }
            result.append(envValue != null && !envValue.isBlank() ? envValue : fallback);
            i = end + 1;
        }
        return result.toString();
    }

    /**
     * 解析时长：支持 {@code 500ms}、{@code 60s}、{@code 10m}、{@code 2h}、
     * 纯数字（按秒）以及 ISO-8601（{@code PT10M}）。
     */
    static Duration parseDuration(String value) {
        var trimmed = value.trim();
        try {
            if (trimmed.matches("\\d+")) {
                return Duration.ofSeconds(Long.parseLong(trimmed));
            }
            if (trimmed.matches("\\d+(ms|s|m|h|d)")) {
                boolean millis = trimmed.endsWith("ms");
                String unitSuffix = millis ? trimmed.substring(trimmed.length() - 2)
                        : trimmed.substring(trimmed.length() - 1);
                long amount = Long.parseLong(trimmed.substring(0, trimmed.length() - unitSuffix.length()));
                return switch (unitSuffix) {
                    case "ms" -> Duration.ofMillis(amount);
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    default -> Duration.ofDays(amount);
                };
            }
            return Duration.parse(trimmed);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid duration '" + value + "'");
        }
    }

    /**
     * 按各模块工厂装配 AgentService。
     *
     * @param props         已解析占位符的配置
     * @param modelOverride 可选的 ChatModel 覆盖（如测试注入桩模型），非 null 时忽略 LLM provider 配置
     */
    static AgentService assemble(Properties props, @Nullable ChatModel modelOverride) {
        var workDir = Path.of(props.getProperty("cloud-ai.context.work-dir", "."));

        // 1. LLM 层
        ModelRouter router;
        if (modelOverride != null) {
            router = new ModelRouter("default");
            router.register("default", modelOverride);
        } else {
            router = LlmAutoConfiguration.modelRouter(buildLlmProperties(props), null, null);
        }

        // 2. 记忆层 + 上下文压缩
        MemoryStore memoryStore = MemoryAutoConfiguration.memoryStore();
        MemoryRetriever retriever = MemoryAutoConfiguration.memoryRetriever(memoryStore);
        ContextManager contextManager = MemoryAutoConfiguration.contextManager();
        int maxContextTokens = intProperty(props, "cloud-ai.agent.max-context-tokens", 8000);

        // 3. 人格层（独立服务器使用内置默认人格；多人格配置走 cloud-ai-spring 或 facade）
        PersonaProvider personaProvider = new DefaultPersonaProvider(new PersonaProperties(null, null));
        PersonaAssembler personaAssembler = new DefaultPersonaAssembler();

        // 4. 安全层 — 默认放行 FILE_READ(**) 与 workspace 内 FILE_WRITE，SHELL_EXEC 需显式审批
        var permissionManager = new DefaultPermissionManager();
        var workspace = props.getProperty("cloud-ai.execution.filesystem.workspace", "");
        permissionManager.allow(OperationType.FILE_READ, "**");
        if (!workspace.isBlank()) {
            permissionManager.allow(OperationType.FILE_WRITE,
                    Path.of(workspace).toAbsolutePath().normalize().toString()
                            .replace('\\', '/') + "/**");
        }
        log.warn("Standalone server allows FILE_READ(**) and FILE_WRITE({}/**) by default; "
                + "SHELL_EXEC is denied unless configured", workspace);
        var securityProps = new SecurityProperties(
                new SecurityProperties.Approval(
                        parseDuration(props.getProperty("cloud-ai.security.approval.timeout", "120s")),
                        props.getProperty("cloud-ai.security.approval.mode", "auto")),
                null);
        SecurityInterceptor securityInterceptor = SecurityAutoConfiguration.securityInterceptor(
                permissionManager,
                SecurityAutoConfiguration.approvalGateway(securityProps),
                SecurityAutoConfiguration.auditLogger());

        // 5. 工具层
        ExecutionProperties executionProps = new ExecutionProperties(
                booleanProperty(props, "cloud-ai.execution.file-read-enabled", true),
                booleanProperty(props, "cloud-ai.execution.file-write-enabled", true),
                booleanProperty(props, "cloud-ai.execution.shell-enabled", true),
                parseDuration(props.getProperty("cloud-ai.execution.shell.timeout", "30s")),
                workspace.isBlank() ? null : Path.of(workspace));
        ToolRegistry toolRegistry = ExecutionAutoConfiguration.toolRegistry(executionProps);
        ToolExecutionService toolExecutionService =
                ExecutionAutoConfiguration.toolExecutionService(toolRegistry, securityInterceptor);

        // 6. 技能层
        SkillRegistry skillRegistry = null;
        if (booleanProperty(props, "cloud-ai.skills.enabled", true)) {
            var skillProps = new SkillProperties();
            skillProps.setWorkDir(workDir);
            var registry = SkillAutoConfiguration.skillRegistry(skillProps);
            if (!registry.list().isEmpty()) {
                skillRegistry = registry;
                toolRegistry.register(LoadSkillExecutor.definition(),
                        SkillAutoConfiguration.loadSkillExecutor((com.cloudai.skills.SkillLoader) registry));
                log.info("Skills registered: {}", registry.list().size());
            }
        }

        // 7. 运行时层
        var runtimeProps = new RuntimeProperties(
                intProperty(props, "cloud-ai.agent.max-turns", 50),
                parseDuration(props.getProperty("cloud-ai.agent.timeout", "10m")),
                AgentType.valueOf(props.getProperty("cloud-ai.agent.type", "react").trim().toUpperCase()),
                intProperty(props, "cloud-ai.agent.max-plan-steps", 10));
        AgentLoop agentLoop = RuntimeAutoConfiguration.agentLoop(
                router, toolRegistry, toolExecutionService, runtimeProps,
                java.util.List.of(), contextManager, maxContextTokens);

        // 8. 服务层
        return new AgentService(agentLoop, personaProvider, personaAssembler,
                retriever, memoryStore, toolRegistry, skillRegistry, workDir);
    }

    /** 启动 HTTP 服务器并注册 Agent 路由（虚拟线程执行器）。 */
    static HttpServer startServer(int port, AgentService agentService, @Nullable String apiKey)
            throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/agent/", new AgentHttpHandler(agentService, apiKey));
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        log.info("Cloud AI HTTP server started on port {}", server.getAddress().getPort());
        return server;
    }

    /** 从 {@code cloud-ai.llm.providers.<name>.*} 扁平键聚合出 LlmProperties。 */
    static LlmProperties buildLlmProperties(Properties props) {
        var providers = new TreeMap<String, Map<String, String>>();
        for (var key : props.stringPropertyNames()) {
            if (!key.startsWith(LLM_PROVIDER_PREFIX)) {
                continue;
            }
            var rest = key.substring(LLM_PROVIDER_PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot <= 0) {
                continue;
            }
            providers.computeIfAbsent(rest.substring(0, dot), k -> new LinkedHashMap<>())
                    .put(rest.substring(dot + 1), props.getProperty(key));
        }

        if (providers.isEmpty()) {
            throw new IllegalStateException("No LLM providers configured. Set cloud-ai.llm.providers."
                    + "<name>.base-url/api-key/model in " + CONFIG_RESOURCE
                    + " (or the OPENAI_API_KEY / DEEPSEEK_API_KEY environment variables).");
        }

        var mapped = new LinkedHashMap<String, ProviderProperties>();
        providers.forEach((name, attrs) -> mapped.put(name, new ProviderProperties(
                attrs.get("base-url"),
                attrs.get("api-key"),
                attrs.get("model"),
                attrs.containsKey("timeout") ? parseDuration(attrs.get("timeout")) : null,
                attrs.containsKey("max-context-tokens")
                        ? Integer.parseInt(attrs.get("max-context-tokens")) : null,
                attrs.containsKey("max-retries") ? Integer.parseInt(attrs.get("max-retries")) : null,
                attrs.containsKey("capabilities")
                        ? java.util.Arrays.asList(attrs.get("capabilities").split("\\s*,\\s*")) : null)));

        var defaultProvider = props.getProperty("cloud-ai.llm.default-provider", "");
        if (defaultProvider.isBlank()) {
            defaultProvider = mapped.keySet().iterator().next();
            log.info("cloud-ai.llm.default-provider not set, using first provider: '{}'", defaultProvider);
        }
        return new LlmProperties(defaultProvider, mapped);
    }

    private static int intProperty(Properties props, String key, int defaultValue) {
        var value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer for '{}': '{}', using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static boolean booleanProperty(Properties props, String key, boolean defaultValue) {
        var value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    @Nullable
    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
