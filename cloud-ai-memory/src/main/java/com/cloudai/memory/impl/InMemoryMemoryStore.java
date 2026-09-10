package com.cloudai.memory.impl;

import com.cloudai.memory.model.MemoryEntry;
import com.cloudai.memory.spi.MemoryStore;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存 ConcurrentHashMap 的记忆存储 — 开发期默认实现。
 *
 * <p>线程安全，进程重启后数据丢失。
 * 生产环境应替换为持久化实现（文件、数据库、向量库）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class InMemoryMemoryStore implements MemoryStore {

    private final ConcurrentHashMap<String, MemoryEntry> store = new ConcurrentHashMap<>();

    @Override
    public void save(MemoryEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        store.put(entry.id(), entry);
    }

    @Override
    public Optional<MemoryEntry> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<MemoryEntry> findByAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        return store.values().stream()
                .filter(e -> agentId.equals(e.agentId()))
                .sorted(java.util.Comparator.comparing(MemoryEntry::timestamp).reversed())
                .toList();
    }

    @Override
    public List<MemoryEntry> findBySession(String agentId, String sessionId) {
        if (agentId == null || agentId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        return store.values().stream()
                .filter(e -> agentId.equals(e.agentId()))
                .filter(e -> sessionId.equals(e.sessionId()))
                .sorted(java.util.Comparator.comparing(MemoryEntry::timestamp).reversed())
                .toList();
    }

    @Override
    public void deleteById(String id) {
        if (id != null && !id.isBlank()) {
            store.remove(id);
        }
    }

    @Override
    public void deleteBySession(String agentId, String sessionId) {
        if (agentId == null || agentId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        store.entrySet().removeIf(entry ->
                agentId.equals(entry.getValue().agentId())
                && sessionId.equals(entry.getValue().sessionId()));
    }

    /** 返回存储条目总数（用于测试）。 */
    public int size() {
        return store.size();
    }
}

