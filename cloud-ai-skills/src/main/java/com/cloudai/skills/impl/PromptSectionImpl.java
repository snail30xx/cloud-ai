package com.cloudai.skills.impl;

import com.cloudai.core.spi.PromptSection;

/**
 * PromptSection 简单实现 — skills 模块内部使用。
 *
 * @author cloud-ai
 * @since 1.0
 */
public record PromptSectionImpl(String name, String content, int order, boolean cacheable) implements PromptSection {

    public static PromptSectionImpl empty(String name) {
        return new PromptSectionImpl(name, "", 50, false);
    }

    public static PromptSectionImpl of(String name, String content, int order) {
        return new PromptSectionImpl(name, content, order, false);
    }
}
