package com.cloudai.server.facade;

import com.cloudai.context.DefaultEnvironmentProvider;
import com.cloudai.context.FilesystemProjectContextProvider;
import com.cloudai.core.prompt.SimplePromptSection;
import com.cloudai.context.ContextProvider;
import com.cloudai.core.prompt.DefaultPromptAssembler;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.core.chat.ChatModel;
import com.cloudai.core.prompt.PromptAssembler;
import com.cloudai.core.prompt.PromptSection;
import com.cloudai.execution.registry.DefaultToolRegistry;
import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.ToolExecutor;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.adapter.DeepSeekLlmAdapter;
import com.cloudai.llm.adapter.OpenAiLlmAdapter;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.memory.InMemoryMemoryStore;
import com.cloudai.memory.KeywordMemoryRetriever;
import com.cloudai.memory.MemoryPromptSection;
import com.cloudai.memory.MemoryEntry;
import com.cloudai.memory.MemoryType;
import com.cloudai.memory.MemoryRetriever;
import com.cloudai.memory.MemoryStore;
import com.cloudai.persona.DefaultPersonaAssembler;
import com.cloudai.persona.DefaultPersonaProvider;
import com.cloudai.persona.PersonaPromptSection;
import com.cloudai.persona.Persona;
import com.cloudai.persona.PersonaAssembler;
import com.cloudai.runtime.AgentLoopFactory;
import com.cloudai.runtime.AgentRequest;
import com.cloudai.runtime.AgentResponse;
import com.cloudai.runtime.AgentType;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.security.permission.DefaultPermissionManager;
import com.cloudai.security.SecurityInterceptor;
import com.cloudai.security.audit.Slf4jAuditLogger;
import com.cloudai.security.approval.ApprovalResponse;
import com.cloudai.security.permission.OperationType;
import com.cloudai.security.approval.ApprovalGateway;
import com.cloudai.security.audit.AuditLogger;
import com.cloudai.security.permission.PermissionManager;
import com.cloudai.skills.LoadSkillExecutor;
import com.cloudai.skills.SkillMenuPromptSection;
import com.cloudai.skills.SkillRegistry;
import com.cloudai.skills.SkillLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cloud AI 门面 — 九层 Agent 框架的统一入口。
 *
 * <p>封装了 LLM 适配、记忆、人格、上下文、技能、安全、工具执行和 Agent 运行时的全部串联逻辑。
 * 用户只需提供 LLM 配置和（可选的）工具/人格/工作目录配置即可运行 Agent。</p>
 *
 * <p>系统提示词由多个 {@link PromptSection} 按 order 排序拼接：</p>
 * <ul>
 *   <li>order=10: Persona（静态）</li>
 *   <li>order=30: Environment（动态）</li>
 *   <li>order=40: Project Context — AGENTS.md/CLAUDE.md（动态）</li>
 *   <li>order=50: Skill Menu（动态）</li>
 *   <li>order=60: Relevant Memories（动态）</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class CloudAi {

    private static final Logger log = LoggerFactory.getLogger(CloudAi.class);
    private static final String DEFAULT_AGENT_ID = "cloud-ai-agent";

    private final AgentLoop agentLoop;
    private final PromptAssembler promptAssembler;
    private final List<PromptSectionProvider> sectionProviders;
    private final MemoryStore memoryStore;
    private final ToolRegistry toolRegistry;
    private final MemoryRetriever memoryRetriever;

    private CloudAi(AgentLoop agentLoop, PromptAssembler promptAssembler,
                    List<PromptSectionProvider> sectionProviders,
                    MemoryRetriever memoryRetriever, MemoryStore memoryStore,
                    ToolRegistry toolRegistry) {
        this.agentLoop = agentLoop;
        this.promptAssembler = promptAssembler;
        this.sectionProviders = List.copyOf(sectionProviders);
        this.memoryRetriever = memoryRetriever;
        this.memoryStore = memoryStore;
        this.toolRegistry = toolRegistry;
    }

    public AgentResponse run(String prompt) {
        return run(prompt, null, null, null, null);
    }

    public AgentResponse run(String prompt, @Nullable String provider, @Nullable Integer maxTurns,
                             @Nullable Duration timeout, @Nullable String traceId) {
        var systemPrompt = buildSystemPrompt(prompt);
        var request = new AgentRequest(prompt, systemPrompt, provider, null,
                maxTurns, timeout, traceId, null);
        var response = agentLoop.run(request);
        persistInteraction(traceId != null ? traceId : response.traceId(), prompt, response);
        return response;
    }

    public void interrupt(String traceId) {
        agentLoop.interrupt(traceId);
    }

    public boolean isRunning(String traceId) {
        return agentLoop.isRunning(traceId);
    }

    public MemoryStore memoryStore() {
        return memoryStore;
    }

    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    private String buildSystemPrompt(String userPrompt) {
        var sections = new ArrayList<PromptSection>();
        for (var provider : sectionProviders) {
            sections.add(provider.build(userPrompt));
        }
        return promptAssembler.assemble(sections);
    }

    private void persistInteraction(String traceId, String prompt, AgentResponse response) {
        try {
            memoryStore.save(MemoryEntry.working(DEFAULT_AGENT_ID, traceId,
                    "Q: " + prompt + "\nA: " + response.content()));
        } catch (Exception e) {
            log.warn("Failed to persist interaction memory: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    public interface PromptSectionProvider {
        PromptSection build(String userPrompt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private ChatModel model;
        private String providerName = "default";

        private final List<ToolRegistration> tools = new ArrayList<>();
        private Persona persona = null;
        private MemoryStore memoryStore = null;
        private int maxTurns = 50;
        private Duration timeout = Duration.ofMinutes(10);
        private AgentType agentType = AgentType.REACT;

        @Nullable private Path workDir = null;
        private final List<PromptSectionProvider> sectionProviders = new ArrayList<>();

        private Builder() {
        }

        // ==================== LLM 配置 ====================

        public Builder openai(String baseUrl, String apiKey, String model) {
            var props = new ProviderProperties(baseUrl, apiKey, model, null, null, null, null);
            this.model = new OpenAiLlmAdapter(props);
            this.providerName = "openai";
            return this;
        }

        public Builder deepseek(String baseUrl, String apiKey, String model) {
            var props = new ProviderProperties(baseUrl, apiKey, model, null, null, null,
                    List.of("chat", "tool_calling", "thinking"));
            this.model = new DeepSeekLlmAdapter(props);
            this.providerName = "deepseek";
            return this;
        }

        public Builder model(ChatModel model, String providerName) {
            this.model = model;
            this.providerName = providerName;
            return this;
        }

        public Builder model(ChatModel model) {
            return model(model, "default");
        }

        // ==================== 可选配置 ====================

        public Builder tool(String name, String description, ToolExecutor executor) {
            this.tools.add(new ToolRegistration(name, description, executor));
            return this;
        }

        public Builder persona(String name, String systemPrompt) {
            this.persona = new Persona(name, name, null, systemPrompt,
                    List.of(), null, List.of());
            return this;
        }

        public Builder persona(Persona persona) {
            this.persona = persona;
            return this;
        }

        public Builder memoryStore(MemoryStore store) {
            this.memoryStore = store;
            return this;
        }

        public Builder memory(String content, MemoryType type, double importance) {
            ensureMemoryStore();
            memoryStore.save(MemoryEntry.of(DEFAULT_AGENT_ID, content, type));
            return this;
        }

        public Builder maxTurns(int maxTurns) {
            this.maxTurns = maxTurns;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder agentType(AgentType type) {
            this.agentType = type;
            return this;
        }

        /**
         * 设置工作目录 — 用于环境信息、AGENTS.md/CLAUDE.md 读取和技能扫描。
         *
         * @param workDir 工作目录路径
         */
        public Builder workDir(Path workDir) {
            this.workDir = workDir;
            return this;
        }

        /**
         * 注册自定义 PromptSection 提供者。
         */
        public Builder promptSection(PromptSectionProvider provider) {
            this.sectionProviders.add(provider);
            return this;
        }

        // ==================== 构建 ====================

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

            // 4. 安全层
            var permissionManager = new DefaultPermissionManager();
            // 默认仅允许低风险操作，SHELL_EXEC 需显式配置
            permissionManager.allow(OperationType.FILE_READ, "**");
            permissionManager.allow(OperationType.FILE_WRITE, "/workspace/**");
            log.warn("CloudAi facade allows FILE_READ(**) and FILE_WRITE(/workspace/**) by default; SHELL_EXEC is denied unless configured");
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

            // 5b. 技能层 — 扫描工作目录下的 skills
            var effectiveWorkDir = workDir != null ? workDir : Path.of(".");
            SkillRegistry skillRegistry = null;
            try {
                var fsr = new com.cloudai.skills.FilesystemSkillRegistry(effectiveWorkDir);
                if (!fsr.list().isEmpty()) {
                    skillRegistry = fsr;
                    var loadExecutor = new LoadSkillExecutor(fsr);
                    toolRegistry.register(LoadSkillExecutor.definition(), loadExecutor);
                    log.info("Skills registered: {}", fsr.list().size());
                }
            } catch (Exception e) {
                log.warn("Skill scanning failed: {}", e.getMessage());
            }

            var toolExecService = new ToolExecutionService(toolRegistry, securityInterceptor);

            // 6. 运行时层
            var agentLoop = AgentLoopFactory.builder(router, toolRegistry, toolExecService)
                    .type(agentType)
                    .maxTurns(maxTurns)
                    .timeout(timeout)
                    .build();

            // 7. 组装 PromptSection 提供者（按 order 排序由 PromptAssembler 处理）
            var providers = new ArrayList<PromptSectionProvider>();

            // [order=10] Persona
            providers.add(userPrompt -> new PersonaPromptSection(persona, assembler, toolRegistry.listDefinitions()));

            // [order=30] Environment
            var envProvider = new DefaultEnvironmentProvider(workDir);
            providers.add(userPrompt -> envProvider.buildSection());

            // [order=40] Project Context (AGENTS.md / CLAUDE.md)
            var projectProvider = new FilesystemProjectContextProvider(workDir);
            providers.add(userPrompt -> projectProvider.buildSection());

            // [order=50] Skill Menu
            if (skillRegistry != null) {
                var skillMenu = new SkillMenuPromptSection(skillRegistry);
                providers.add(userPrompt -> skillMenu.build());
            }

            // [order=60] Relevant Memories
            providers.add(userPrompt -> new MemoryPromptSection(retriever, DEFAULT_AGENT_ID, userPrompt));

            // 用户自定义段落
            providers.addAll(sectionProviders);

            var promptAssembler = new DefaultPromptAssembler();

            log.info("CloudAi initialized: provider='{}', model={}, tools={}, persona='{}', workDir={}, skills={}, sections={}",
                    providerName,
                    model.getClass().getSimpleName(),
                    toolRegistry.size(),
                    persona.name(),
                    effectiveWorkDir,
                    skillRegistry != null ? skillRegistry.list().size() : 0,
                    providers.size());

            return new CloudAi(agentLoop, promptAssembler, providers, retriever, memoryStore, toolRegistry);
        }

        private void ensureMemoryStore() {
            if (memoryStore == null) {
                memoryStore = new InMemoryMemoryStore();
            }
        }

        private record ToolRegistration(String name, String description, ToolExecutor executor) {}
    }
}

