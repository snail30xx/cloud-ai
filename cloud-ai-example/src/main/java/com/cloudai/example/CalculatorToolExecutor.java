package com.cloudai.example;

import com.cloudai.core.model.ToolCall;
import com.cloudai.execution.model.ToolResult;
import com.cloudai.execution.spi.ToolExecutor;

/**
 * 计算器工具 — 评估简单算术表达式（加、减、乘、除）。
 *
 * <p>仅用于示例演示，支持两个操作数的二元运算。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class CalculatorToolExecutor implements ToolExecutor {

    @Override
    public ToolResult execute(ToolCall toolCall) {
        var expression = extractExpression(toolCall.arguments());
        try {
            var result = evaluate(expression);
            return ToolResult.success(toolCall.id(), result);
        } catch (Exception e) {
            return ToolResult.failure(toolCall.id(), "Cannot evaluate: " + e.getMessage());
        }
    }

    /** 从 JSON 参数字符串中提取 expression 字段值。 */
    private static String extractExpression(String arguments) {
        // 简化解析：{"expression":"25 * 4"} → 25 * 4
        if (arguments == null || arguments.isBlank()) {
            throw new IllegalArgumentException("arguments is empty");
        }
        var key = "\"expression\"";
        var idx = arguments.indexOf(key);
        if (idx < 0) {
            throw new IllegalArgumentException("missing 'expression' field");
        }
        var colonIdx = arguments.indexOf(':', idx);
        var startQuote = arguments.indexOf('"', colonIdx + 1);
        var endQuote = arguments.indexOf('"', startQuote + 1);
        return arguments.substring(startQuote + 1, endQuote).trim();
    }

    /** 评估简单二元算术表达式。 */
    static String evaluate(String expr) {
        expr = expr.trim();
        // 按优先级尝试运算符
        for (String op : new String[]{"+", "-", "*", "/"}) {
            int idx = findOperator(expr, op);
            if (idx > 0) {
                double a = Double.parseDouble(expr.substring(0, idx).trim());
                double b = Double.parseDouble(expr.substring(idx + 1).trim());
                double result = switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    case "/" -> b == 0 ? Double.POSITIVE_INFINITY : a / b;
                    default -> throw new IllegalStateException();
                };
                if (result == (long) result) {
                    return String.valueOf((long) result);
                }
                return String.valueOf(result);
            }
        }
        throw new IllegalArgumentException("Cannot parse expression: " + expr);
    }

    /** 查找运算符位置（跳过开头的负号）。 */
    private static int findOperator(String expr, String op) {
        int start = expr.startsWith("-") ? 1 : 0;
        return expr.indexOf(op, start);
    }
}
