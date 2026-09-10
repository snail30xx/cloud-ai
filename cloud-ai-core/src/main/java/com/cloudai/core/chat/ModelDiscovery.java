package com.cloudai.core.chat;

import com.cloudai.core.chat.ModelInfo;

import java.util.List;

/**
 * 模型发现能力 — 可选的增强接口，与 {@link ChatModel} 解耦。
 *
 * <p>实现此接口的 {@code ChatModel} 可以提供模型元信息和模型列表。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface ModelDiscovery {

    /** 获取当前模型信息 */
    ModelInfo getModelInfo();

    /** 列出所有可用模型 */
    List<ModelInfo> listModels();
}