package com.cloudai.core.model;

import java.util.List;

/**
 * 模型信息。
 *
 * @param provider     提供商
 * @param model        模型名称
 * @param maxTokens    最大上下文 Token
 * @param capabilities 能力列表
 */
public record ModelInfo(String provider, String model, int maxTokens, List<String> capabilities) {
    public ModelInfo {
        capabilities = List.copyOf(capabilities);
    }
}