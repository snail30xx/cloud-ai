package com.cloudai.runtime;

import com.cloudai.core.chat.ChatResponse;
import com.cloudai.runtime.AgentSession;

/**
 * 自定义停止条件 — 在每轮 LLM 响应后检查，满足时终止循环。
 *
 * <p>示例：Token 预算耗尽、关键词命中、工具调用次数超限等。
 * 多个条件为 OR 关系：任一满足即停止。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@FunctionalInterface
public interface StopCondition {

    /**
     * 检查是否应停止循环。
     *
     * @param session      当前会话状态
     * @param lastResponse 最近一次 LLM 响应
     * @return true 表示应停止
     */
    boolean shouldStop(AgentSession session, ChatResponse lastResponse);
}