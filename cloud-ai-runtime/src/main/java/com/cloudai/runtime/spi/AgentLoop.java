package com.cloudai.runtime.spi;

import com.cloudai.runtime.model.AgentRequest;
import com.cloudai.runtime.model.AgentResponse;

/**
 * Agent 循环 SPI — 执行 ReAct 循环（LLM 调用 → 工具执行 → 结果反馈 → 重复）。
 *
 * <p>运行时核心入口：接收用户输入，驱动 LLM 和工具执行，直到获得最终回答
 * 或达到终止条件（最大轮次、超时、中断、自定义停止条件、错误）。</p>
 *
 * <p>中断与恢复：通过 {@link #interrupt(String)} 可从外部线程协作式中断运行中的会话，
 * 返回的 {@link AgentResponse#history()} 可用于构造新的 {@link AgentRequest#resume} 继续执行。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface AgentLoop {

    /**
     * 执行 Agent 循环（阻塞直至终止）。
     *
     * @param request Agent 调用请求
     * @return 执行结果
     */
    AgentResponse run(AgentRequest request);

    /**
     * 中断指定 traceId 的运行中会话。
     *
     * <p>线程安全：可从任意线程调用。被中断的循环在下一个迭代点返回
     * {@link AgentResponse.FinishStatus#INTERRUPTED}。</p>
     *
     * @param traceId 追踪 ID
     */
    default void interrupt(String traceId) {
        // 默认空实现，具体实现可覆盖
    }

    /**
     * 检查指定 traceId 的会话是否正在运行。
     *
     * @param traceId 追踪 ID
     * @return true 表示正在运行
     */
    default boolean isRunning(String traceId) {
        return false;
    }
}