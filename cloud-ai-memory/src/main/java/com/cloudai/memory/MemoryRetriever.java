package com.cloudai.memory;

import com.cloudai.memory.MemoryEntry;
import com.cloudai.memory.MemoryQuery;

import java.util.List;

/**
 * 记忆检索器 — 根据查询文本检索相关记忆。
 *
 * <p>实现可以是关键词匹配、向量检索或混合检索。
 * 返回结果按相关性降序排列。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface MemoryRetriever {

    /**
     * 检索与查询文本相关的记忆。
     *
     * @param query 检索查询
     * @return 按相关性降序排列的记忆列表
     */
    List<MemoryEntry> retrieve(MemoryQuery query);
}
