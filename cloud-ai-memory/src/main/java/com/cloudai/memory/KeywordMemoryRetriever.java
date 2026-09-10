package com.cloudai.memory;

import com.cloudai.memory.MemoryEntry;
import com.cloudai.memory.MemoryQuery;
import com.cloudai.memory.MemoryType;
import com.cloudai.memory.MemoryRetriever;
import com.cloudai.memory.MemoryStore;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于关键词匹配的记忆检索器 — 默认实现。
 *
 * <p>将查询文本拆分为关键词（非字母数字字符分割，转小写），
 * 对每条记忆计算匹配关键词数作为相关性分数，
 * 叠加 importance 权重后降序排列。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class KeywordMemoryRetriever implements MemoryRetriever {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "to", "of", "in", "on", "at", "for", "with", "and", "or", "not",
            "i", "you", "he", "she", "it", "we", "they", "this", "that");

    private final MemoryStore store;

    public KeywordMemoryRetriever(MemoryStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
    }

    @Override
    public List<MemoryEntry> retrieve(MemoryQuery query) {
        if (query == null) {
            return List.of();
        }

        var keywords = tokenize(query.queryText());
        if (keywords.isEmpty()) {
            return List.of();
        }

        var candidates = store.findByAgent(query.agentId());
        if (query.sessionId() != null && !query.sessionId().isBlank()) {
            candidates = candidates.stream()
                    .filter(e -> query.sessionId().equals(e.sessionId()))
                    .toList();
        }
        if (query.typeFilter() != null && !query.typeFilter().isEmpty()) {
            var types = Set.copyOf(query.typeFilter());
            candidates = candidates.stream()
                    .filter(e -> types.contains(e.type()))
                    .toList();
        }

        return candidates.stream()
                .map(entry -> scoreEntry(entry, keywords))
                .filter(scored -> scored.score > 0)
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                .limit(query.maxResults())
                .map(ScoredMemory::entry)
                .toList();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(token -> token.length() > 1)
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toSet());
    }

    private ScoredMemory scoreEntry(MemoryEntry entry, Set<String> keywords) {
        var contentTokens = tokenize(entry.content());
        long matchCount = keywords.stream()
                .filter(contentTokens::contains)
                .count();
        if (matchCount == 0) {
            return new ScoredMemory(entry, 0);
        }
        // 相关性 = 匹配关键词比例 * 0.7 + importance * 0.3
        double relevance = (double) matchCount / keywords.size();
        double score = relevance * 0.7 + entry.importance() * 0.3;
        return new ScoredMemory(entry, score);
    }

    private record ScoredMemory(MemoryEntry entry, double score) {}
}
