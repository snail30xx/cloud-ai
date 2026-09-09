package com.cloudai.core.spi;

import java.util.List;

/**
 * 提示词组装器 — 将多个 {@link PromptSection} 按序拼接为最终 system prompt。
 *
 * <p>实现应按 {@link PromptSection#order()} 升序排列所有段落，
 * 用分隔符连接，并可选地标记 static/dynamic 边界。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@FunctionalInterface
public interface PromptAssembler {

    /**
     * 将段落列表组装为最终 system prompt 字符串。
     *
     * @param sections 段落列表，不能为 null
     * @return 拼接后的 system prompt
     */
    String assemble(List<PromptSection> sections);
}
