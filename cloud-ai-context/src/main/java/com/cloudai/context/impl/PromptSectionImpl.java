package com.cloudai.context.impl;

import com.cloudai.core.spi.PromptSection;

/**
 * PromptSection 简单实现 — 用于返回空段落或固定内容。
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

    public static PromptSectionImpl cached(String name, String content, int order) {
        return new PromptSectionImpl(name, content, order, true);
    }
}
