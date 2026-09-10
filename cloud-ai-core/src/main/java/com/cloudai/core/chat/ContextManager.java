package com.cloudai.core.chat;

import java.util.List;

/**
 * 上下文管理器 — 管理对话历史的 token 预算。
 *
 * <p>当对话历史超出 token 上限时，对旧消息进行压缩
 * （截断或摘要），确保 LLM 调用不会超长。</p>
 *
 * <p>作为共享词汇定义在 core：实现方（如 memory 模块的
 * {@code SimpleContextManager}）与消费方（runtime 模块的
 * Agent 循环在每次调用 LLM 前裁剪请求视图）跨模块协作，
 * 会话原始历史不受影响。</p>
 *
 * <p>实现必须线程安全或仅在单线程（循环线程）中使用。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface ContextManager {

    /**
     * 压缩消息列表以适应 token 预算。
     *
     * <p>策略：保留 system 消息和最近的对话，
     * 对中间的旧消息做截断或摘要。</p>
     *
     * @param messages  原始消息列表
     * @param maxTokens 最大 token 数
     * @return 压缩后的消息列表
     */
    List<Message> compress(List<Message> messages, int maxTokens);

    /**
     * 估算消息列表的 token 数。
     *
     * @param messages 消息列表
     * @return 估算 token 数
     */
    int estimateTokens(List<Message> messages);
}
