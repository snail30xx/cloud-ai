package com.cloudai.runtime.loop;

import com.cloudai.core.chat.ChatModel;
import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.ContextManager;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.Message;
import com.cloudai.core.chat.TokenUsage;
import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.DefaultToolRegistry;
import com.cloudai.llm.ModelRouter;
import com.cloudai.runtime.AgentRequest;
import com.cloudai.runtime.AgentResponse;
import com.cloudai.security.SecurityInterceptor;
import com.cloudai.security.approval.ApprovalGateway;
import com.cloudai.security.approval.ApprovalResponse;
import com.cloudai.security.audit.Slf4jAuditLogger;
import com.cloudai.security.permission.DefaultPermissionManager;
import com.cloudai.security.permission.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("上下文压缩接入 AgentLoop")
class ContextCompressionTest {

    private CapturingChatModel chatModel;
    private ReActAgentLoop loop;

    @BeforeEach
    void setUp() {
        chatModel = new CapturingChatModel();
        var router = new ModelRouter("stub");
        router.register("stub", chatModel);

        var permissionManager = new DefaultPermissionManager();
        permissionManager.allow(OperationType.CUSTOM, "/**");
        ApprovalGateway autoApprove = request -> ApprovalResponse.approved("auto-approved");
        var executionService = new ToolExecutionService(
                new DefaultToolRegistry(), new SecurityInterceptor(permissionManager, autoApprove, new Slf4jAuditLogger()));

        loop = new ReActAgentLoop(router, new DefaultToolRegistry(), executionService,
                10, Duration.ofMinutes(5), List.of());
    }

    private ReActAgentLoop loopWithManager(ContextManager manager, int maxTokens) {
        var router = new ModelRouter("stub");
        router.register("stub", chatModel);
        var permissionManager = new DefaultPermissionManager();
        permissionManager.allow(OperationType.CUSTOM, "/**");
        ApprovalGateway autoApprove = request -> ApprovalResponse.approved("auto-approved");
        var executionService = new ToolExecutionService(
                new DefaultToolRegistry(), new SecurityInterceptor(permissionManager, autoApprove, new Slf4jAuditLogger()));
        return new ReActAgentLoop(router, new DefaultToolRegistry(), executionService,
                10, Duration.ofMinutes(5), List.of(), manager, maxTokens);
    }

    private static AgentRequest requestWithHistory(List<Message> history) {
        return new AgentRequest("latest question", null, null, null,
                null, null, "trace-compress", history);
    }

    private static List<Message> longHistory(int pairs) {
        var history = new ArrayList<Message>();
        history.add(Message.system("You are a helpful assistant."));
        for (int i = 1; i <= pairs; i++) {
            history.add(Message.user("question " + i));
            history.add(Message.assistant("answer " + i));
        }
        return history;
    }

    @Nested
    @DisplayName("未配置 ContextManager（默认）")
    class NoManager {

        @Test
        @DisplayName("完整历史原样传给 LLM — 行为与接入前一致")
        void fullHistoryPassedToModel() {
            var history = longHistory(5);

            var response = loop.run(requestWithHistory(history));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, response.finishStatus());
            var request = chatModel.lastRequest.get();
            assertNotNull(request);
            // system + 5 对问答 + 最新 user = 12 条
            assertEquals(12, request.messages().size());
        }
    }

    @Nested
    @DisplayName("配置 ContextManager")
    class WithManager {

        @Test
        @DisplayName("超预算历史被裁剪，system 消息保留，会话原始历史不受影响")
        void overBudgetHistoryTrimmed() {
            var history = longHistory(5);
            var trimmingLoop = loopWithManager(new KeepLastMessages(2), 1000);

            var response = trimmingLoop.run(requestWithHistory(history));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, response.finishStatus());
            var request = chatModel.lastRequest.get();
            assertNotNull(request);
            // system + 最近 2 条（answer 5 + 新 user）= 3 条，远小于 12
            assertEquals(3, request.messages().size());
            assertEquals("system", request.messages().get(0).role());
            assertTrue(request.messages().get(1).content().contains("answer 5"));

            // 会话原始历史未被破坏：system + 10 条 + 新 user + assistant = 13
            assertEquals(13, response.history().size());
        }

        @Test
        @DisplayName("预算内历史不裁剪")
        void underBudgetHistoryUnchanged() {
            var history = longHistory(1);
            var trimmingLoop = loopWithManager(new KeepLastMessages(10), 1000);

            trimmingLoop.run(requestWithHistory(history));

            var request = chatModel.lastRequest.get();
            assertNotNull(request);
            assertEquals(4, request.messages().size());
        }

        @Test
        @DisplayName("空历史边界 — 仅 system + user")
        void emptyHistoryBorder() {
            var trimmingLoop = loopWithManager(new KeepLastMessages(2), 1000);

            var response = trimmingLoop.run(new AgentRequest("hi", null, null, null,
                    null, null, "trace-empty", List.of()));

            assertEquals(AgentResponse.FinishStatus.COMPLETED, response.finishStatus());
            var request = chatModel.lastRequest.get();
            assertNotNull(request);
            assertEquals(1, request.messages().size());
            assertEquals("user", request.messages().get(0).role());
        }
    }

    /** 记录最后一次请求的桩模型，固定返回纯文本回答。 */
    private static final class CapturingChatModel implements ChatModel {
        final AtomicReference<ChatRequest> lastRequest = new AtomicReference<>();

        @Override
        public ChatResponse call(ChatRequest request) {
            lastRequest.set(request);
            return ChatResponse.of("done", List.of(), new TokenUsage(1, 1), FinishReason.STOP);
        }
    }

    /** 保留全部 system 消息 + 最近 N 条非 system 消息的桩压缩器。 */
    private static final class KeepLastMessages implements ContextManager {
        private final int keep;

        KeepLastMessages(int keep) {
            this.keep = keep;
        }

        @Override
        public List<Message> compress(List<Message> messages, int maxTokens) {
            var system = messages.stream().filter(Message::isSystem).toList();
            var conversation = messages.stream().filter(m -> !m.isSystem()).toList();
            var kept = conversation.subList(Math.max(0, conversation.size() - keep), conversation.size());
            var result = new ArrayList<Message>(system.size() + kept.size());
            result.addAll(system);
            result.addAll(kept);
            return List.copyOf(result);
        }

        @Override
        public int estimateTokens(List<Message> messages) {
            return messages.size() * 10;
        }
    }
}
