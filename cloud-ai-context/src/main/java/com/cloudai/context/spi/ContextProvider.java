package com.cloudai.context.spi;

import com.cloudai.core.spi.PromptSection;

/**
 * 上下文提供者 SPI — 提供动态上下文段落（环境信息、项目上下文等）。
 *
 * <p>每个实现返回一个 {@link PromptSection}，由 {@code PromptAssembler}
 * 按 order 排序后拼入 system prompt。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@FunctionalInterface
public interface ContextProvider {

    /**
     * 构建上下文段落。
     *
     * @return PromptSection，content 为空时自动跳过
     */
    PromptSection buildSection();
}
