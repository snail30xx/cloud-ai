package com.cloudai.core.model;

/**
 * Token 使用统计。
 *
 * @param inputTokens  输入 Token
 * @param outputTokens 输出 Token
  * @author cloud-ai
 * @since 1.0
*/
public record TokenUsage(int inputTokens, int outputTokens) {
    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
