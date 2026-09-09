package com.cloudai.server.facade;

import com.cloudai.core.model.ToolDefinition;
import com.cloudai.core.spi.ChatModel;
import com.cloudai.execution.impl.DefaultToolRegistry;
import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolExecutor;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.adapter.DeepSeekLlmAdapter;
import com.cloudai.llm.adapter.OpenAiLlmAdapter;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.memory.impl.InMemoryMemoryStore;
import com.cloudai.memory.impl.KeywordMemoryRetriever;
import com.cloudai.memory.model.MemoryEntry;
import com.cloudai.memory.model.MemoryQuery;
import com.cloudai.memory.model.MemoryType;
import com.cloudai.memory.spi.MemoryRetriever;
import com.cloudai.memory.spi.MemoryStore;
import com.cloudai.persona.impl.DefaultPersonaAssembler;
import com.cloudai.persona.impl.DefaultPersonaProvider;
import com.cloudai.persona.model.Persona;
import com.cloudai.persona.spi.PersonaAssembler;
import com.cloudai.runtime.AgentLoopFactory;
import com.cloudai.runtime.model.AgentRequest;
import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.runtime.model.AgentType;
import com.cloudai.runtime.spi.AgentLoop;
import com.cloudai.security.impl.DefaultPermissionManager;
import com.cloudai.security.impl.SecurityInterceptor;
import com.cloudai.security.impl.Slf4jAuditLogger;
import com.cloudai.security.model.ApprovalResponse;
import com.cloudai.security.model.OperationType;
import com.cloudai.security.spi.ApprovalGateway;
import com.cloudai.security.spi.AuditLogger;
import com.cloudai.security.spi.PermissionManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cloud AI 门面 — 七层 Agent 框架的统一入口。
 *
 * <p>封装了 LLM 适配、记忆、人格、安全、工具执行和 Agent 运行时的全部串联逻辑。
 * 用户只需提供 LLM 配置和（可选的）工具/人格配置即可运行 Agent。</p>
 *
 * <h3>接入 OpenAI</h3>
 * <pre>{@code
 * CloudAi agent = CloudAi.builder()
 *     .openai("https://api.openai.com/v1", System.getenv("OPENAI_API_KEY"), "gpt-4o")
 *     .tool("calculator", "Evaluate arithmetic", new MyCalculator())
 *     .build();
 *
 * AgentResponse response = agent.run("calculate 25 * 4");
 * }</pre>
 *
 * <h3>接入 DeepSeek</h3>
 * <pre>{@code
 * CloudAi agent = CloudAi.builder()
 *     .deepseek("https://api.deepseek.com", System.getenv("DEEPSEEK_API_KEY"), "deepseek-v4-pro")
 *     .build();
 *
 * AgentResponse response = agent.run("你好");
 * }</pre>
 *
 * <h3>自定义 LLM</h3>
 * <pre>{@code
 * CloudAi agent = CloudAi.builder()
 *     .model(new MyChatModel())
 *     .provider("custom")
 *     .build();
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class CloudAi {

    private static final Logger log = LoggerFactory.getLogger(CloudAi.class);
    private static final String DEFAULT_AGENT_ID = "cloud-ai-agent";

    private final AgentLoop agentLoop;
    private final Persona persona;
    private final PersonaAssembler personaAssembler;
    private final MemoryRetriever memoryRetriever;
    private final MemoryStore memoryStore;
    private final ToolRegistry toolRegistry;

    private CloudAi(AgentLoop agentLoop, Persona persona, PersonaAssembler personaAssembler,
                    MemoryRetriever memoryRetriever, MemoryStore memoryStore,
                    ToolRegistry toolRegistry) {
        this.agentLoop = agentLoop;
        this.persona = persona;
        this.personaAssembler = personaAssembler;
        this.memoryRetriever = memoryRetriever;
        this.memoryStore = memoryStore;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 运行 Agent 循环（同步阻塞直到终止）。
     *
     * @param prompt 用户输入
     * @return Agent 执行结果
     */
    public AgentResponse run(String prompt) {
        return run(prompt, null, null, null, null);
    }

    /**
     * 运行 Agent 循环（带可选参数）。
     *
     * @param prompt    用户输入
     * @param provider  LLM provider，null 用默认
     * @param maxTurns  最大轮次，null 用默认
     * @param timeout   超时，null 用默认
     * @param traceId   追踪 ID，null 自动生成
     */
    public AgentResponse run(String prompt, @Nullable String provider, @Nullable Integer maxTurns,
                             @Nullable Duration timeout, @Nullable String traceId) {
        var systemPrompt = buildSystemPrompt(prompt);

        var request = new AgentRequest(prompt, systemPrompt, provider, null,
                maxTurns, timeout, traceId, null);
        var response = agentLoop.run(request);

        persistInteraction(traceId != null ? traceId : response.traceId(), prompt, response);
        return response;
    }

    /** 中断运行中的会话。 */
    public void interrupt(String traceId) {
        agentLoop.interrupt(traceId);
    }

    /** 查询会话是否正在运行。 */
    public boolean isRunning(String traceId) {
        return agentLoop.isRunning(traceId);
    }

    /** 获取记忆存储（用于外部读写记忆）。 */
    public MemoryStore memoryStore() {
        return memoryStore;
    }

    /** 获取工具注册表（用于运行时动态注册工具）。 */
    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    private String buildSystemPrompt(String userPrompt) {
        var tools = toolRegistry.listDefinitions();
        var base = personaAssembler.assemble(persona, tools);

        var memories = memoryRetriever.retrieve(
                MemoryQuery.of(DEFAULT_AGENT_ID, userPrompt));
        if (memories.isEmpty()) {
            return base;
        }

        var sb = new StringBuilder(base).append("\n\n[Relevant Memories]\n");
        for (var m : memories) {
            sb.append("- (").append(m.type()).append(") ").append(m.content()).append("\n");
        }
        sb.append("[/Relevant Memories]");
        return sb.toString();
    }

    private void persistInteraction(String traceId, String prompt, AgentResponse response) {
        try {
            memoryStore.save(MemoryEntry.working(DEFAULT_AGENT_ID, traceId,
                    "Q: " + prompt + "\nA: " + response.content()));
        } catch (Exception e) {
            log.warn("Failed to persist interaction memory: {}", e.getMessage());
        }
    }

    // ==================== Builder ====================

    /**
     * 创建 Builder。
     *
     * @return 新的 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Cloud Ai 门面 Builder — 链式配置，默认串联七层。
     *
     * <p>必填：调用 {@code .openai(...)}、{@code .deepseek(...)} 或 {@code .model(...)} 之一指定 LLM。
     * 其余全部可选，有合理默认值。</p>
     */
    public static final class Builder {

        // LLM 配置（三选一）
        private ChatModel model;
        private String providerName = "default";

        // 可选配置
        private final List<ToolRegistration> tools = new ArrayList<>();
        private Persona persona = null;
        private MemoryStore memoryStore = null;
        private int maxTurns = 50;
        private Duration timeout = Duration.ofMinutes(10);
        private AgentType agentType = AgentType.REACT;

        private Builder() {
        }

        // ==================== LLM 配置 ====================

        /**
         * 接入 OpenAI（或任何 OpenAI 兼容 API）。
         *
         * <pre>{@code
         * .openai("https://api.openai.com/v1", "sk-xxx", "gpt-4o")
         * }</pre>
         *
         * @param baseUrl API 地址
         * @param apiKey API 密钥
         * @param model  模型名
         */
        public Builder openai(String baseUrl, String apiKey, String model) {
            var props = new ProviderProperties(baseUrl, apiKey, model, null, null, null, null);
            this.model = new OpenAiLlmAdapter(props);
            this.providerName = "openai";
            return this;
        }

        /**
         * 接入 DeepSeek（支持 thinking 能力）。
         *
         * <pre>{@code
         * .deepseek("https://api.deepseek.com", "sk-xxx", "deepseek-v4-pro")
         * }</pre>
         *
         * @param baseUrl API 地址
         * @param apiKey API 密钥
         * @param model  模型名
         */
        public Builder deepseek(String baseUrl, String apiKey, String model) {
            var props = new ProviderProperties(baseUrl, apiKey, model, null, null, null,
                    List.of("chat", "tool_calling", "thinking"));
            this.model = new DeepSeekLlmAdapter(props);
            this.providerName = "deepseek";
            return this;
        }

        /**
         * 自定义 LLM 实现（如 Stub、Mock 或其他适配器）。
         *
         * @param model         ChatModel 实例
         * @param providerName  provider 名称
         */
        public Builder model(ChatModel model, String providerName) {
            this.model = model;
            this.providerName = providerName;
            return this;
        }

        /**
         * 自定义 LLM 实现（provider 名默认为 "default"）。
         */
        public Builder model(ChatModel model) {
            return model(model, "default");
        }

        // ==================== 可选配置 ====================

        /**
         * 注册工具。
         */
        public Builder tool(String name, String description, ToolExecutor executor) {
            this.tools.add(new ToolRegistration(name, description, executor));
            return this;
        }

        /**
         * 设置人格（默认使用内置 Assistant 人格）。
         */
        public Builder persona(String name, String systemPrompt) {
            this.persona = new Persona(name, name, null, systemPrompt,
                    List.of(), null, List.of());
            return this;
        }

        /**
         * 设置完整人格定义。
         */
        public Builder persona(Persona persona) {
            this.persona = persona;
            return this;
        }

        /**
         * 设置记忆存储（默认 InMemoryMemoryStore）。
         */
        public Builder memoryStore(MemoryStore store) {
            this.memoryStore = store;
            return this;
        }

        /**
         * 预存一条语义记忆。
         */
        public Builder memory(String content, MemoryType type, double importance) {
            ensureMemoryStore();
            memoryStore.save(MemoryEntry.of(DEFAULT_AGENT_ID, content, type));
            return this;
        }

        /**
         * 设置最大轮次（默认 50）。
         */
        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        /**
         * 设置超时（默认 10 分钟）。
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * 设置 Agent 类型（默认 REACT）。
         */
        public Builder agentType(AgentType type) {
            this.agentType = type;
            return this;
        }

        // ==================== 构建 ====================

        /**
         * 构建 Cloud Ai 门面实例，自动串联七层。
         */
        public CloudAi build() {
            if (model == null) {
                throw new IllegalStateException(
                        "LLM model not configured — call .openai(...), .deepseek(...) or .model(...) first");
            }

            // 1. LLM 层
            var router = new ModelRouter(providerName);
            router.register(providerName, model);

            // 2. 记忆层
            if (memoryStore == null) {
                memoryStore = new InMemoryMemoryStore();
            }
            var retriever = new KeywordMemoryRetriever(memoryStore);

            // 3. 人格层
            if (persona == null) {
                persona = DefaultPersonaProvider.builtinDefault();
            }
            var assembler = new DefaultPersonaAssembler();

            // 4. 安全层 — 默认允许全部操作 + 自动审批
            var permissionManager = new DefaultPermissionManager();
            for (var op : OperationType.values()) {
                permissionManager.allow(op, "*");
            }
            ApprovalGateway autoApprove = req -> ApprovalResponse.approved("auto-approved");
            var auditLogger = new Slf4jAuditLogger();
            var securityInterceptor = new SecurityInterceptor(
                    permissionManager, autoApprove, auditLogger);

            // 5. 工具层
            var toolRegistry = new DefaultToolRegistry();
            for (var t : tools) {
                toolRegistry.register(
                        new ToolDefinition(t.name, t.description, Map.of("type", "object")),
                        t.executor);
            }
            var toolExecService = new ToolExecutionService(toolRegistry, securityInterceptor);

            // 6. 运行时层
            var agentLoop = AgentLoopFactory.builder(router, toolRegistry, toolExecService)
                    .type(agentType)
                    .maxTurns(maxTurns)
                    .timeout(timeout)
                    .build();

            log.info("CloudAi initialized: provider='{}', model={}, tools={}, persona='{}', memory={}",
                    providerName,
                    model.getClass().getSimpleName(),
                    tools.size(), persona.name(),
                    memoryStore.getClass().getSimpleName());

            return new CloudAi(agentLoop, persona, assembler, retriever, memoryStore, toolRegistry);
        }

        private void ensureMemoryStore() {
            if (memoryStore == null) {
                memoryStore = new InMemoryMemoryStore();
            }
        }

        private record ToolRegistration(String name, String description, ToolExecutor executor) {}
    }
}
