package com.cloudai.example;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.ChatResponse;
import com.cloudai.core.model.FinishReason;
import com.cloudai.core.model.Message;
import com.cloudai.core.model.TokenUsage;
import com.cloudai.core.model.ToolCall;
import com.cloudai.core.spi.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Stub LLM — 模拟两轮 ReAct 对话，无需真实 API Key。
 *
 * <p>Turn 1：收到用户消息后，提取算术表达式，返回 calculator 工具调用。
 * Turn 2：收到工具执行结果后，返回最终文本回答。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class StubChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(StubChatModel.class);
    private int callCount = 0;

    @Override
    public ChatResponse call(ChatRequest request) {
        callCount++;
        var messages = request.messages();

        // 找到最后一条用户消息
        var lastUserMessage = findLastUserMessage(messages);
        // 判断是否有工具结果消息
        var hasToolResult = hasToolResult(messages);

        if (!hasToolResult && lastUserMessage != null) {
            // Turn 1：返回工具调用
            var expression = extractExpression(lastUserMessage);
            log.info("[Stub LLM] Turn 1: received prompt='{}', extracting expression='{}'", lastUserMessage, expression);

            var toolCall = new ToolCall("call_001", "calculator",
                    "{\"expression\":\"" + expression + "\"}");

            log.info("[Stub LLM] Turn 1: requesting tool call: calculator({})", expression);
            return ChatResponse.of(
                    "stub-1", "stub-model",
                    "I will calculate " + expression + " for you.",
                    List.of(toolCall),
                    new TokenUsage(100, 20),
                    FinishReason.TOOL_CALLS,
                    java.util.Map.of());
        }

        // Turn 2：返回最终回答
        var toolResult = findToolResult(messages);
        log.info("[Stub LLM] Turn 2: received tool result='{}', generating final answer", toolResult);

        var answer = "The result is " + toolResult + ".";
        log.info("[Stub LLM] Turn 2: final answer='{}'", answer);

        return ChatResponse.of(
                "stub-2", "stub-model",
                answer,
                List.of(),
                new TokenUsage(50, 30),
                FinishReason.STOP,
                java.util.Map.of());
    }

    private String findLastUserMessage(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isUser()) {
                return messages.get(i).content();
            }
        }
        return null;
    }

    private boolean hasToolResult(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isTool()) {
                return true;
            }
        }
        return false;
    }

    private String findToolResult(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isTool()) {
                return messages.get(i).content();
            }
        }
        return "unknown";
    }

    /** 从用户输入中提取算术表达式，如 "calculate 25 * 4" → "25 * 4"。 */
    private static String extractExpression(String input) {
        if (input == null) {
            return "0";
        }
        var lower = input.toLowerCase();
        var markers = new String[]{"calculate ", "compute ", "eval ", "what is ", "计算 "};
        for (var marker : markers) {
            var idx = lower.indexOf(marker);
            if (idx >= 0) {
                return input.substring(idx + marker.length()).trim().replaceAll("[?.!]", "");
            }
        }
        // 如果没有标记词，尝试直接提取数字和运算符
        return input.trim().replaceAll("[?.!]", "");
    }
}
