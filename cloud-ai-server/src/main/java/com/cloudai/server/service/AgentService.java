package com.cloudai.server.service;

import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.memory.model.MemoryEntry;
import com.cloudai.memory.model.MemoryQuery;
import com.cloudai.memory.spi.MemoryRetriever;
import com.cloudai.memory.spi.MemoryStore;
import com.cloudai.persona.model.Persona;
import com.cloudai.persona.spi.PersonaAssembler;
import com.cloudai.persona.spi.PersonaProvider;
import com.cloudai.runtime.model.AgentRequest;
import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.runtime.spi.AgentLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 服务 — 七层集成的编排入口。
 *
 * <p>将人格层、记忆层、运行时层串联：
 * <ol>
 *   <li>从 {@link PersonaProvider} 解析人格，经 {@link PersonaAssembler} 组装 system prompt</li>
 *   <li>从 {@link MemoryRetriever} 检索相关记忆，追加到 system prompt</li>
 *   <li>构建 {@link AgentRequest}，调用 {@link AgentLoop#run} 执行</li>
 *   <li>执行完成后，将本次交互存入 {@link MemoryStore} 作为工作记忆</li>
 * </ol>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String AGENT_ID = "cloud-ai-agent";

    private final AgentLoop agentLoop;
    private final PersonaProvider personaProvider;
    private final PersonaAssembler personaAssembler;
    private final MemoryRetriever memoryRetriever;
    private final MemoryStore memoryStore;
    private final ToolRegistry toolRegistry;

    public AgentService(AgentLoop agentLoop,
                        PersonaProvider personaProvider,
                        PersonaAssembler personaAssembler,
                        MemoryRetriever memoryRetriever,
                        MemoryStore memoryStore,
                        ToolRegistry toolRegistry) {
        this.agentLoop = agentLoop;
        this.personaProvider = personaProvider;
        this.personaAssembler = personaAssembler;
        this.memoryRetriever = memoryRetriever;
        this.memoryStore = memoryStore;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 运行 Agent 循环。
     *
     * @param prompt    用户输入
     * @param provider  LLM provider，null 时用默认
     * @param maxTurns  最大轮次，null 时用默认
     * @param timeout   超时，null 时用默认
     * @param traceId   追踪 ID，null 时自动生成
     * @return Agent 执行结果
     */
    public AgentResponse run(String prompt, String provider, Integer maxTurns,
                             java.time.Duration timeout, String traceId) {
        var systemPrompt = buildSystemPrompt(prompt);
        log.info("Agent run: prompt='{}...', systemPrompt={} chars", truncate(prompt, 50), systemPrompt.length());

        var request = new AgentRequest(prompt, systemPrompt, provider, null, maxTurns, timeout, traceId, null);
        var response = agentLoop.run(request);

        // 将本次交互存为工作记忆
        persistInteraction(traceId != null ? traceId : response.traceId(), prompt, response);

        return response;
    }

    /**
     * 检查指定会话是否正在运行。
     */
    public boolean isRunning(String traceId) {
        return agentLoop.isRunning(traceId);
    }

    /**
     * 中断指定会话。
     */
    public void interrupt(String traceId) {
        agentLoop.interrupt(traceId);
    }

    private String buildSystemPrompt(String userPrompt) {
        // 1. 组装人格 system prompt
        var persona = personaProvider.defaultPersona();
        var tools = toolRegistry.listDefinitions();
        var basePrompt = personaAssembler.assemble(persona, tools);

        // 2. 检索相关记忆
        var query = MemoryQuery.of(AGENT_ID, userPrompt);
        var memories = memoryRetriever.retrieve(query);

        if (memories.isEmpty()) {
            return basePrompt;
        }

        // 3. 将记忆追加到 system prompt
        var memoryBlock = formatMemories(memories);
        return basePrompt + "\n\n" + memoryBlock;
    }

    private String formatMemories(List<MemoryEntry> memories) {
        var sb = new StringBuilder("[Relevant Memories]\n");
        for (var entry : memories) {
            sb.append("- (").append(entry.type()).append(") ").append(entry.content()).append("\n");
        }
        sb.append("[/Relevant Memories]");
        return sb.toString();
    }

    private void persistInteraction(String traceId, String prompt, AgentResponse response) {
        try {
            var content = "Q: " + prompt + "\nA: " + response.content();
            var entry = MemoryEntry.working(AGENT_ID, traceId, content);
            memoryStore.save(entry);
            log.debug("Persisted interaction as working memory: traceId={}", traceId);
        } catch (Exception e) {
            log.warn("Failed to persist interaction memory: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
