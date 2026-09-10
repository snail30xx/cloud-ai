package com.cloudai.security.approval;

import com.cloudai.core.tool.ToolCall;
import com.cloudai.security.RiskLevel;
import com.cloudai.security.permission.OperationType;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

/**
 * 审批请求。
 *
 * @param operation 操作描述
 * @param risk      风险等级
 * @param context   审批上下文（额外信息）
 * @param timeout   审批超时时间（null 时由网关使用默认值）
 * @param toolCall  原始工具调用（null 表示不携带）
 * @author cloud-ai
 * @since 1.0
 */
public record ApprovalRequest(
        String operation,
        RiskLevel risk,
        @Nullable Map<String, Object> context,
        @Nullable Duration timeout,
        @Nullable ToolCall toolCall) {

    public ApprovalRequest {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (risk == null) {
            throw new IllegalArgumentException("risk must not be null");
        }
        context = context != null ? Map.copyOf(context) : Map.of();
    }

    /** 便捷构造：低风险操作，使用默认超时。 */
    public static ApprovalRequest lowRisk(String operation) {
        return new ApprovalRequest(operation, RiskLevel.LOW, Map.of(), null, null);
    }

    /** 便捷构造：指定风险等级。 */
    public static ApprovalRequest of(String operation, RiskLevel risk) {
        return new ApprovalRequest(operation, risk, Map.of(), null, null);
    }

    /** 便捷构造：携带工具调用，自动评估风险等级。 */
    public static ApprovalRequest of(ToolCall toolCall) {
        var opType = OperationType.from(toolCall.name());
        var risk = RiskLevel.from(opType);
        return new ApprovalRequest(opType.name(), risk, Map.of(), null, toolCall);
    }

    /** 便捷构造：携带工具调用，指定风险等级。 */
    public static ApprovalRequest of(ToolCall toolCall, RiskLevel risk) {
        return new ApprovalRequest(toolCall.name(), risk, Map.of(), null, toolCall);
    }
}
