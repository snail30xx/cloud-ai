package com.cloudai.runtime;

import com.cloudai.core.chat.Message;
import com.cloudai.core.chat.TokenUsage;
import com.cloudai.security.SecurityContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agent 会话上下文 — 维护单次 Agent 循环运行期间的对话历史和运行时状态。
 *
 * <p>核心状态：
 * <ul>
 *   <li>{@code traceId} — 全链路追踪 ID，贯穿 LLM 调用、工具执行和安全审计</li>
 *   <li>{@code history} — 对话历史（可变，循环过程中追加）</li>
 *   <li>{@code startTime} — 会话启动时间，用于超时检测</li>
 *   <li>{@code interrupted} — 中断标志，支持外部线程协作式中断</li>
 *   <li>{@code toolCallsExecuted} — 累计工具执行次数</li>
 * </ul>
 *
 * <p>线程安全：{@code interrupted} 为 {@code volatile}，
 * {@code history} 仅在单线程（循环线程）中修改，
 * {@code traceId} 和 {@code startTime} 不可变。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class AgentSession {

    private final String traceId;
    private final List<Message> history = new ArrayList<>();
    private final SecurityContext securityContext;
    private final Instant startTime;
    private int turnsExecuted;
    private int toolCallsExecuted;
    private int inputTokens;
    private int outputTokens;
    private volatile boolean interrupted;

    /**
     * @param traceId 全链路追踪 ID，同时用作 SecurityContext.sessionId
     * @param agentId Agent 标识
     */
    public AgentSession(String traceId, String agentId) {
        this.traceId = traceId;
        this.securityContext = new SecurityContext(agentId, null, traceId, java.util.Map.of());
        this.startTime = Instant.now();
    }

    /** 追加消息到对话历史。 */
    public void addMessage(Message message) {
        history.add(message);
    }

    /** 递增 LLM 调用轮次（仅在调用成功后调用）。 */
    public void incrementTurn() {
        turnsExecuted++;
    }

    /** 递增工具执行计数。 */
    public void incrementToolCall() {
        toolCallsExecuted++;
    }

    /** 累加 Token 用量。 */
    public void accumulateUsage(TokenUsage usage) {
        if (usage != null) {
            inputTokens += usage.inputTokens();
            outputTokens += usage.outputTokens();
        }
    }

    /** 设置中断标志（可从其他线程调用）。 */
    public void interrupt() {
        interrupted = true;
    }

    /** 是否已被中断。 */
    public boolean isInterrupted() {
        return interrupted;
    }

    /** 是否已超时。 */
    public boolean isTimedOut(Duration timeout) {
        if (timeout == null) {
            return false;
        }
        return Duration.between(startTime, Instant.now()).compareTo(timeout) >= 0;
    }

    /** 全链路追踪 ID。 */
    public String traceId() {
        return traceId;
    }

    /** 对话历史不可变快照。 */
    public List<Message> history() {
        return List.copyOf(history);
    }

    /** 安全上下文。 */
    public SecurityContext securityContext() {
        return securityContext;
    }

    /** 已执行的 LLM 调用轮次。 */
    public int turnsExecuted() {
        return turnsExecuted;
    }

    /** 已执行的工具调用总数。 */
    public int toolCallsExecuted() {
        return toolCallsExecuted;
    }

    /** 累计 Token 统计。 */
    public TokenUsage totalUsage() {
        return new TokenUsage(inputTokens, outputTokens);
    }

    /** 会话启动时间。 */
    public Instant startTime() {
        return startTime;
    }
}