package com.cloudai.memory.model;

/**
 * 记忆类型 — 区分不同时效和用途的记忆。
 *
 * @author cloud-ai
 * @since 1.0
 */
public enum MemoryType {

    /** 工作记忆 — 当前会话的短期上下文，会话结束后可压缩为情节记忆。 */
    WORKING,

    /** 情节记忆 — 过去交互的记录（谁、何时、做了什么、结果如何）。 */
    EPISODIC,

    /** 语义记忆 — 关于用户、世界、领域的事实和知识。 */
    SEMANTIC,

    /** 程序记忆 — 学到的操作模式和技能（如何完成某类任务）。 */
    PROCEDURAL;
}
