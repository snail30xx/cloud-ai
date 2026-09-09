package com.cloudai.memory.advisor;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.Message;
import com.cloudai.llm.advisor.Advisor;
import com.cloudai.memory.model.MemoryEntry;
import com.cloudai.memory.model.MemoryQuery;
import com.cloudai.memory.spi.MemoryRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆 Advisor — 在 LLM 调用前检索相关记忆并注入到上下文。
 *
 * <p>从对话历史中提取最新用户消息作为检索文本，
 * 调用 {@link MemoryRetriever} 检索相关记忆，
 * 将记忆以 system 消息形式注入到消息列表头部。</p>
 *
 * <p>仅做检索（读），不做存储（写）。
 * 记忆的写入由调用方在会话结束后通过 {@link com.cloudai.memory.spi.MemoryStore} 完成。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class MemoryAdvisor implements Advisor {

    private static final Logger log = LoggerFactory.getLogger(MemoryAdvisor.class);

    private final MemoryRetriever retriever;
    private final String agentId;
    private final int maxResults;

    /**
     * @param retriever  记忆检索器
     * @param agentId    Agent 标识（用于限定检索范围）
     * @param maxResults 最大检索条数
     */
    public MemoryAdvisor(MemoryRetriever retriever, String agentId, int maxResults) {
        if (retriever == null) {
            throw new IllegalArgumentException("retriever must not be null");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        this.retriever = retriever;
        this.agentId = agentId;
        this.maxResults = maxResults;
    }

    @Override
    public ChatRequest advise(ChatRequest request, AdvisorChain chain) {
        var lastUserMessage = findLastUserMessage(request.messages());
        if (lastUserMessage == null) {
            return chain.next(request);
        }

        var query = new MemoryQuery(agentId, lastUserMessage, maxResults, null, null);
        var memories = retriever.retrieve(query);
        if (memories.isEmpty()) {
            return chain.next(request);
        }

        log.debug("MemoryAdvisor retrieved {} memory entry/entries for agent '{}'", memories.size(), agentId);

        var memoryContext = formatMemories(memories);
        var augmented = new java.util.ArrayList<>(request.messages());
        // 在消息列表头部（system 消息之后）插入记忆上下文
        augmented.add(0, Message.system(memoryContext));

        var newRequest = new ChatRequest(List.copyOf(augmented), request.tools(), request.options());
        return chain.next(newRequest);
    }

    private String findLastUserMessage(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            var msg = messages.get(i);
            if (msg.isUser() && msg.content() != null && !msg.content().isBlank()) {
                return msg.content();
            }
        }
        return null;
    }

    private String formatMemories(List<MemoryEntry> memories) {
        var sb = new StringBuilder();
        sb.append("[Relevant Memories]\n");
        for (var entry : memories) {
            sb.append("- (").append(entry.type()).append(", importance=").append(entry.importance()).append(") ");
            sb.append(entry.content()).append("\n");
        }
        sb.append("[/Relevant Memories]");
        return sb.toString();
    }
}
