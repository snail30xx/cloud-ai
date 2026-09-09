package com.cloudai.runtime.model;

import com.cloudai.core.model.ChatResponse;


/**
 * 单轮执行结果 — 表示一次 Turn 的产出。
 *
 * @param response     本轮 LLM 响应
 * @param terminal     是否终止循环（COMPLETED / STOP_CONDITION）
 * @param finishStatus 终止原因（terminal=true 时非 null）
 * @param content      终止时的文本内容（terminal=true 时为最终回答）
 * @author cloud-ai
 * @since 1.0
 */
public record TurnResult(
        ChatResponse response,
        boolean terminal,
        AgentResponse.FinishStatus finishStatus,
        String content) {

    /** 非终止：还有工具调用需要继续循环。 */
    public static TurnResult continuing(ChatResponse response) {
        return new TurnResult(response, false, null, null);
    }

    /** 终止：循环结束。 */
    public static TurnResult terminal(ChatResponse response,
                                      AgentResponse.FinishStatus status,
                                      String content) {
        return new TurnResult(response, true, status, content);
    }
}