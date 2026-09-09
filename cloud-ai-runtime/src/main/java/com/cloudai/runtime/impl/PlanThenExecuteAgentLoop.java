package com.cloudai.runtime.impl;

import com.cloudai.core.model.ChatResponse;
import com.cloudai.core.model.Message;
import com.cloudai.core.model.ToolCall;
import com.cloudai.execution.impl.ToolExecutionService;
import com.cloudai.execution.spi.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.model.AgentRequest;
import com.cloudai.runtime.model.AgentSession;
import com.cloudai.runtime.spi.StopCondition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Plan-then-Execute 模式 Agent 循环。
 *
 * <p>与 ReAct 的差异：
 * <ul>
 *   <li><b>规划提示词</b>：未指定 systemPrompt 时自动注入规划指令，
 *       要求 LLM 在一轮中输出全部工具调用（计划），而非逐轮交替</li>
 *   <li><b>阶段追踪</b>：首轮为 PLANNING，后续轮为 SYNTHESIS，
 *       通过 {@link #beforeTurn} 记录阶段转换</li>
 *   <li><b>计划步数上限</b>：规划阶段（首轮）的工具调用数超过 {@code maxPlanSteps} 时截断，
 *       防止 LLM 生成过大的计划</li>
 * </ul>
 *
 * <p>继承 {@link ReActAgentLoop} 复用 Session/Step 逻辑，
 * 仅覆盖 3 个扩展点，体现模板方法的可扩展性。</p>
 *
 * <pre>{@code
 * Turn 1 (PLANNING):  LLM 输出计划（N 个 tool calls）→ 截断到 maxPlanSteps → 逐个执行
 * Turn 2 (SYNTHESIS): LLM 综合所有结果 → 输出最终回答 → COMPLETED
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class PlanThenExecuteAgentLoop extends ReActAgentLoop {

    private static final Logger log = LoggerFactory.getLogger(PlanThenExecuteAgentLoop.class);

    /** 默认规划提示词。 */
    public static final String DEFAULT_PLANNING_PROMPT = """
            You are a planning agent. Analyze the user's request and plan ALL necessary tool \
            calls in a single response. Do not reason between individual tool calls — output \
            them all at once. After execution, you will receive the results and should \
            synthesize a final answer.""";

    /** 默认最大计划步数。 */
    private static final int DEFAULT_MAX_PLAN_STEPS = 10;

    private final int maxPlanSteps;

    /**
     * @param modelRouter          模型路由器
     * @param toolRegistry         工具注册表
     * @param toolExecutionService 工具执行服务（含安全拦截）
     * @param defaultMaxTurns      默认最大 LLM 调用轮次
     * @param defaultTimeout       默认运行超时
     * @param stopConditions       自定义停止条件列表（可为空）
     * @param maxPlanSteps         规划阶段最大工具调用数
     */
    public PlanThenExecuteAgentLoop(ModelRouter modelRouter,
                                    ToolRegistry toolRegistry,
                                    ToolExecutionService toolExecutionService,
                                    int defaultMaxTurns,
                                    Duration defaultTimeout,
                                    List<StopCondition> stopConditions,
                                    int maxPlanSteps) {
        super(modelRouter, toolRegistry, toolExecutionService,
                defaultMaxTurns, defaultTimeout, stopConditions);
        if (maxPlanSteps <= 0) {
            throw new IllegalArgumentException("maxPlanSteps must be positive");
        }
        this.maxPlanSteps = maxPlanSteps;
    }

    /**
     * 使用默认 maxPlanSteps=10 构造。
     */
    public PlanThenExecuteAgentLoop(ModelRouter modelRouter,
                                    ToolRegistry toolRegistry,
                                    ToolExecutionService toolExecutionService,
                                    int defaultMaxTurns,
                                    Duration defaultTimeout,
                                    List<StopCondition> stopConditions) {
        this(modelRouter, toolRegistry, toolExecutionService,
                defaultMaxTurns, defaultTimeout, stopConditions, DEFAULT_MAX_PLAN_STEPS);
    }

    // ==================== 扩展点覆盖 ====================

    /**
     * Session 级扩展：未指定 systemPrompt 时注入规划提示词。
     */
    @Override
    public void onSessionStart(AgentSession session, AgentRequest request) {
        if (request.systemPrompt() == null || request.systemPrompt().isBlank()) {
            var withPlanPrompt = new AgentRequest(
                    request.userPrompt(), DEFAULT_PLANNING_PROMPT,
                    request.provider(), request.options(), request.maxTurns(),
                    request.timeout(), request.traceId(), request.history());
            super.onSessionStart(session, withPlanPrompt);
            log.info("Injected planning system prompt: traceId={}", session.traceId());
        } else {
            super.onSessionStart(session, request);
        }
    }

    /**
     * Turn 级扩展：追踪阶段（PLANNING / SYNTHESIS）。
     */
    @Override
    public void beforeTurn(AgentSession session) {
        var phase = session.turnsExecuted() == 0 ? "PLANNING" : "SYNTHESIS";
        log.info("[{}] Turn {} starting: traceId={}",
                phase, session.turnsExecuted() + 1, session.traceId());
    }

    /**
     * Step 级扩展：规划阶段限制计划步数。
     *
     * <p>首轮（PLANNING）返回的工具调用数超过 {@code maxPlanSteps} 时，
     * 截断到前 {@code maxPlanSteps} 个，剩余被丢弃。
     * 非首轮（SYNTHESIS）不限制。</p>
     */
    @Override
    public void executeSteps(AgentSession session, List<ToolCall> toolCalls) {
        var limited = toolCalls;
        if (session.turnsExecuted() == 1 && toolCalls.size() > maxPlanSteps) {
            log.warn("Plan exceeds max steps: {} > {}, truncating: traceId={}",
                    toolCalls.size(), maxPlanSteps, session.traceId());
            limited = toolCalls.subList(0, maxPlanSteps);
        }
        for (var toolCall : limited) {
            beforeStep(session, toolCall);
            var result = doStep(session, toolCall);
            var formatted = formatStepResult(result);
            session.addMessage(Message.tool(toolCall.id(), formatted));
            session.incrementToolCall();
            afterStep(session, toolCall, result);
        }
    }
}