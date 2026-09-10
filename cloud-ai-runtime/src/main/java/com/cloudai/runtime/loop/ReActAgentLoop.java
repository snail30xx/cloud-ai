package com.cloudai.runtime.loop;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.Message;
import com.cloudai.core.tool.ToolCall;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.ToolResult;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.AgentRequest;
import com.cloudai.runtime.AgentResponse;
import com.cloudai.runtime.AgentSession;
import com.cloudai.runtime.lifecycle.SessionLifecycle;
import com.cloudai.runtime.StopCondition;
import com.cloudai.runtime.lifecycle.StepLifecycle;
import com.cloudai.runtime.lifecycle.TurnLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReAct 模式 Agent 循环实现。
 *
 * <p>继承 {@link AgentLoopTemplate} 模板骨架，同时实现三个生命周期接口：
 * <ul>
 *   <li>{@link SessionLifecycle} — traceId 生成、会话注册到 {@link ConcurrentHashMap} 支持 interrupt、
 *       resume 时从预设历史初始化对话</li>
 *   <li>{@link TurnLifecycle} — 通过 {@link ModelRouter} 调用 LLM，
 *       遍历 {@link StopCondition} 检查停止条件</li>
 *   <li>{@link StepLifecycle} — 通过 {@link ToolExecutionService} 执行工具（含安全拦截）</li>
 * </ul>
 *
 * <p>模板方法 {@link AgentLoopTemplate#run} 不可覆盖，
 * 三个接口的策略方法由本类实现。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ReActAgentLoop extends AgentLoopTemplate
        implements SessionLifecycle, TurnLifecycle, StepLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ReActAgentLoop.class);
    private static final String AGENT_ID = "cloud-ai-agent";

    private final ModelRouter modelRouter;
    private final ToolRegistry toolRegistry;

    /** 工具注册表（package-private，供同包测试使用）。 */
    ToolRegistry toolRegistry() {
        return toolRegistry;
    }
    private final ToolExecutionService toolExecutionService;
    private final int defaultMaxTurns;
    private final Duration defaultTimeout;
    private final List<StopCondition> stopConditions;

    private final ConcurrentHashMap<String, AgentSession> runningSessions = new ConcurrentHashMap<>();

    /**
     * @param modelRouter          模型路由器
     * @param toolRegistry         工具注册表
     * @param toolExecutionService 工具执行服务（含安全拦截）
     * @param defaultMaxTurns      默认最大 LLM 调用轮次
     * @param defaultTimeout       默认运行超时
     * @param stopConditions       自定义停止条件列表（可为空）
     */
    public ReActAgentLoop(ModelRouter modelRouter,
                          ToolRegistry toolRegistry,
                          ToolExecutionService toolExecutionService,
                          int defaultMaxTurns,
                          Duration defaultTimeout,
                          List<StopCondition> stopConditions) {
        if (modelRouter == null) {
            throw new IllegalArgumentException("modelRouter must not be null");
        }
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry must not be null");
        }
        if (toolExecutionService == null) {
            throw new IllegalArgumentException("toolExecutionService must not be null");
        }
        if (defaultMaxTurns <= 0) {
            throw new IllegalArgumentException("defaultMaxTurns must be positive");
        }
        if (defaultTimeout == null || defaultTimeout.isNegative() || defaultTimeout.isZero()) {
            throw new IllegalArgumentException("defaultTimeout must be positive");
        }
        this.modelRouter = modelRouter;
        this.toolRegistry = toolRegistry;
        this.toolExecutionService = toolExecutionService;
        this.defaultMaxTurns = defaultMaxTurns;
        this.defaultTimeout = defaultTimeout;
        this.stopConditions = stopConditions != null ? List.copyOf(stopConditions) : List.of();
    }

    // ==================== 策略注入 ====================

    @Override
    protected SessionLifecycle sessionLifecycle() {
        return this;
    }

    @Override
    protected TurnLifecycle turnLifecycle() {
        return this;
    }

    @Override
    protected StepLifecycle stepLifecycle() {
        return this;
    }

    // ==================== SessionLifecycle ====================

    @Override
    public AgentSession createSession(AgentRequest request) {
        var traceId = resolveTraceId(request.traceId());
        return new AgentSession(traceId, AGENT_ID);
    }

    @Override
    public int resolveMaxTurns(AgentRequest request) {
        return request.maxTurns() != null ? request.maxTurns() : defaultMaxTurns;
    }

    @Override
    public Duration resolveTimeout(AgentRequest request) {
        return request.timeout() != null ? request.timeout() : defaultTimeout;
    }

    @Override
    public void registerSession(AgentSession session) {
        runningSessions.put(session.traceId(), session);
    }

    @Override
    public void unregisterSession(AgentSession session) {
        runningSessions.remove(session.traceId());
    }

    @Override
    public void onSessionStart(AgentSession session, AgentRequest request) {
        if (request.history() != null && !request.history().isEmpty()) {
            for (var msg : request.history()) {
                session.addMessage(msg);
            }
            session.addMessage(Message.user(request.userPrompt()));
            log.debug("Resumed conversation with {} message(s)", request.history().size());
        } else {
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                session.addMessage(Message.system(request.systemPrompt()));
            }
            session.addMessage(Message.user(request.userPrompt()));
        }
    }

    @Override
    public void interrupt(String traceId) {
        var session = runningSessions.get(traceId);
        if (session != null) {
            log.info("Interrupting session: traceId={}", traceId);
            session.interrupt();
        } else {
            log.warn("No running session found for interrupt: traceId={}", traceId);
        }
    }

    @Override
    public boolean isRunning(String traceId) {
        return runningSessions.containsKey(traceId);
    }

    // ==================== TurnLifecycle ====================

    @Override
    public List<ToolDefinition> resolveTools(AgentRequest request) {
        return toolRegistry.listDefinitions();
    }

    @Override
    public ChatResponse callModel(AgentSession session, AgentRequest request) {
        var tools = toolRegistry.listDefinitions();
        var chatRequest = new ChatRequest(session.history(), tools, request.options());

        if (request.provider() != null && !request.provider().isBlank()) {
            log.debug("Calling LLM: provider={}, tools={}", request.provider(), tools.size());
            return modelRouter.chat(request.provider(), chatRequest);
        }
        log.debug("Calling LLM: default provider, tools={}", tools.size());
        return modelRouter.chatDefault(chatRequest);
    }

    @Override
    public AgentResponse.FinishStatus checkStopConditions(AgentSession session, ChatResponse response) {
        for (var sc : stopConditions) {
            if (sc.shouldStop(session, response)) {
                return AgentResponse.FinishStatus.STOP_CONDITION;
            }
        }
        return null;
    }

    // ==================== StepLifecycle ====================

    @Override
    public ToolResult doStep(AgentSession session, ToolCall toolCall) {
        if (toolCall.name() == null || toolCall.name().isBlank()) {
            log.warn("Skipping tool call with empty name: id={}", toolCall.id());
            return ToolResult.failure(toolCall.id(), "empty tool name");
        }
        log.debug("Executing tool: name={}, id={}", toolCall.name(), toolCall.id());
        return toolExecutionService.execute(toolCall, session.securityContext());
    }

    // ==================== 内部方法 ====================

    private static String resolveTraceId(String provided) {
        return (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString();
    }
}



