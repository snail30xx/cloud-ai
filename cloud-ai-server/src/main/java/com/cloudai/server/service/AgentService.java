package com.cloudai.server.service;

import com.cloudai.context.impl.DefaultEnvironmentProvider;
import com.cloudai.context.impl.FilesystemProjectContextProvider;
import com.cloudai.core.impl.DefaultPromptAssembler;
import com.cloudai.core.spi.PromptAssembler;
import com.cloudai.core.spi.PromptSection;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.memory.impl.MemoryPromptSection;
import com.cloudai.memory.model.MemoryEntry;
import com.cloudai.memory.model.MemoryQuery;
import com.cloudai.memory.spi.MemoryRetriever;
import com.cloudai.memory.spi.MemoryStore;
import com.cloudai.persona.impl.PersonaPromptSection;
import com.cloudai.persona.model.Persona;
import com.cloudai.persona.spi.PersonaAssembler;
import com.cloudai.persona.spi.PersonaProvider;
import com.cloudai.runtime.model.AgentRequest;
import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.runtime.spi.AgentLoop;
import com.cloudai.skills.impl.SkillMenuPromptSection;
import com.cloudai.skills.spi.SkillRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 服务 — 通过 PromptSection 体系编排系统提示词。
 *
 * <p>各层提供自己的 PromptSection，由 PromptAssembler 按 order 排序拼接：
 * <ol>
 *   <li>Persona (order=10)</li>
 *   <li>Environment (order=30)</li>
 *   <li>Project Context (order=40)</li>
 *   <li>Skill Menu (order=50)</li>
 *   <li>Relevant Memories (order=60)</li>
 * </ol>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String AGENT_ID = "cloud-ai-agent";

    private final AgentLoop agentLoop;
    private final PersonaProvider personaProvider;
    private final PersonaAssembler personaAssembler;
    private final MemoryRetriever memoryRetriever;
    private final MemoryStore memoryStore;
    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final PromptAssembler promptAssembler;
    private final DefaultEnvironmentProvider envProvider;
    private final FilesystemProjectContextProvider projectProvider;
    private final java.nio.file.Path workDir;

    public AgentService(AgentLoop agentLoop,
                        PersonaProvider personaProvider,
                        PersonaAssembler personaAssembler,
                        MemoryRetriever memoryRetriever,
                        MemoryStore memoryStore,
                        ToolRegistry toolRegistry,
                        SkillRegistry skillRegistry,
                        java.nio.file.Path workDir) {
        this.agentLoop = agentLoop;
        this.personaProvider = personaProvider;
        this.personaAssembler = personaAssembler;
        this.memoryRetriever = memoryRetriever;
        this.memoryStore = memoryStore;
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
        this.workDir = workDir;
        this.promptAssembler = new DefaultPromptAssembler();
        this.envProvider = new DefaultEnvironmentProvider(workDir);
        this.projectProvider = new FilesystemProjectContextProvider(workDir);
    }

    public AgentResponse run(String prompt, String provider, Integer maxTurns,
                             Duration timeout, String traceId) {
        var systemPrompt = buildSystemPrompt(prompt);
        log.info("Agent run: prompt='{}...', systemPrompt={} chars",
                truncate(prompt, 50), systemPrompt.length());

        var request = new AgentRequest(prompt, systemPrompt, provider, null, maxTurns, timeout, traceId, null);
        var response = agentLoop.run(request);
        persistInteraction(traceId != null ? traceId : response.traceId(), prompt, response);
        return response;
    }

    public boolean isRunning(String traceId) {
        return agentLoop.isRunning(traceId);
    }

    public void interrupt(String traceId) {
        agentLoop.interrupt(traceId);
    }

    private String buildSystemPrompt(String userPrompt) {
        var sections = new ArrayList<PromptSection>();

        // [order=10] Persona
        Persona persona = personaProvider.defaultPersona();
        var tools = toolRegistry.listDefinitions();
        sections.add(new PersonaPromptSection(persona, personaAssembler, tools));

        // [order=30] Environment
        sections.add(envProvider.buildSection());

        // [order=40] Project Context
        sections.add(projectProvider.buildSection());

        // [order=50] Skill Menu
        if (skillRegistry != null) {
            var skillMenu = new SkillMenuPromptSection(skillRegistry);
            sections.add(skillMenu.build());
        }

        // [order=60] Relevant Memories
        var query = MemoryQuery.of(AGENT_ID, userPrompt);
        var memories = memoryRetriever.retrieve(query);
        if (!memories.isEmpty()) {
            sections.add(new MemoryPromptSection(memoryRetriever, AGENT_ID, userPrompt));
        }

        return promptAssembler.assemble(sections);
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

