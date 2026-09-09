package com.cloudai.memory.spi;

import com.cloudai.memory.model.MemoryEntry;

import java.util.List;
import java.util.Optional;

/**
 * 记忆存储 — 负责记忆条目的持久化 CRUD。
 *
 * <p>实现可以是内存、文件、数据库或向量存储。
 * 线程安全由实现保证。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface MemoryStore {

    /** 保存记忆条目。 */
    void save(MemoryEntry entry);

    /** 按 ID 查找。 */
    Optional<MemoryEntry> findById(String id);

    /** 查找指定 Agent 的所有记忆。 */
    List<MemoryEntry> findByAgent(String agentId);

    /** 查找指定 Agent 在指定会话中的记忆。 */
    List<MemoryEntry> findBySession(String agentId, String sessionId);

    /** 按 ID 删除。 */
    void deleteById(String id);

    /** 删除指定 Agent 在指定会话中的所有记忆。 */
    void deleteBySession(String agentId, String sessionId);
}
