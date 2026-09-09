package com.cloudai.memory.impl;

import com.cloudai.core.spi.PromptSection;
import com.cloudai.memory.model.MemoryEntry;
import com.cloudai.memory.model.MemoryQuery;
import com.cloudai.memory.spi.MemoryRetriever;

import java.util.List;

/**
 * 记忆提示词段落 — 检索相关记忆并组装为 order=60 的动态段落。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class MemoryPromptSection implements PromptSection {

    private final MemoryRetriever retriever;
    private final String agentId;
    private final String userPrompt;

    public MemoryPromptSection(MemoryRetriever retriever, String agentId, String userPrompt) {
        this.retriever = retriever;
        this.agentId = agentId;
        this.userPrompt = userPrompt;
    }

    @Override
    public String name() {
        return "RelevantMemories";
    }

    @Override
    public String content() {
        var memories = retriever.retrieve(MemoryQuery.of(agentId, userPrompt));
        if (memories.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder("[Relevant Memories]\n");
        for (MemoryEntry m : memories) {
            sb.append("- (").append(m.type()).append(") ").append(m.content()).append("\n");
        }
        sb.append("[/Relevant Memories]");
        return sb.toString();
    }

    @Override
    public int order() {
        return 60;
    }
}
