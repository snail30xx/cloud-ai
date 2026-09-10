package com.cloudai.memory;

import com.cloudai.core.chat.Message;
import com.cloudai.memory.ContextManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单上下文管理器 — 截断策略。
 *
 * <p>当消息列表超出 token 预算时：
 * <ul>
 *   <li>保留所有 system 消息（不截断）</li>
 *   <li>保留最近的非 system 消息</li>
 *   <li>从最早的对话消息开始截断</li>
 *   <li>被截断的消息不保留（不生成摘要）</li>
 * </ul>
 *
 * <p>Token 估算使用粗略启发：约 4 个字符 ≈ 1 token。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class SimpleContextManager implements ContextManager {

    private static final int CHARS_PER_TOKEN = 4;

    @Override
    public List<Message> compress(List<Message> messages, int maxTokens) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        if (maxTokens <= 0) {
            return List.of();
        }

        var all = new ArrayList<>(messages);
        if (estimateTokens(all) <= maxTokens) {
            return List.copyOf(all);
        }

        // 分离 system 消息和对话消息
        var systemMessages = new ArrayList<Message>();
        var conversation = new ArrayList<Message>();
        for (var msg : all) {
            if (msg.isSystem()) {
                systemMessages.add(msg);
            } else {
                conversation.add(msg);
            }
        }

        // 从最早的消息开始移除，直到满足预算
        int systemTokens = sumTokens(systemMessages);
        int budget = maxTokens - systemTokens;
        if (budget <= 0) {
            // system 消息已超预算，只保留 system
            return List.copyOf(systemMessages);
        }

        // 从末尾（最新）向前保留，直到预算用尽
        var kept = new ArrayList<Message>();
        int used = 0;
        for (int i = conversation.size() - 1; i >= 0; i--) {
            int msgTokens = estimateMessageTokens(conversation.get(i));
            if (used + msgTokens > budget) {
                break;
            }
            kept.add(0, conversation.get(i));
            used += msgTokens;
        }

        var result = new ArrayList<Message>(systemMessages.size() + kept.size());
        result.addAll(systemMessages);
        result.addAll(kept);
        return List.copyOf(result);
    }

    @Override
    public int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return sumTokens(messages);
    }

    private int sumTokens(List<Message> messages) {
        int total = 0;
        for (var msg : messages) {
            total += estimateMessageTokens(msg);
        }
        return total;
    }

    private int estimateMessageTokens(Message message) {
        int chars = 0;
        if (message.content() != null) {
            chars += message.content().length();
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            for (var tc : message.toolCalls()) {
                if (tc.name() != null) {
                    chars += tc.name().length();
                }
                if (tc.arguments() != null) {
                    chars += tc.arguments().length();
                }
            }
        }
        // 每条消息固定开销（角色标记等）
        return (chars / CHARS_PER_TOKEN) + 4;
    }
}
