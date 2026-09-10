package com.cloudai.core.prompt;

/**
 * PromptSection 简单实现 — 承载固定或空内容的标准提示词段落。
 *
 * <p>各模块需要构造一次性段落时复用此类型，不在模块内再建副本。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public record SimplePromptSection(String name, String content, int order, boolean cacheable) implements PromptSection {

    /** 空段落（默认排序值 50，不缓存）。 */
    public static SimplePromptSection empty(String name) {
        return new SimplePromptSection(name, "", 50, false);
    }

    /** 固定内容段落（不缓存）。 */
    public static SimplePromptSection of(String name, String content, int order) {
        return new SimplePromptSection(name, content, order, false);
    }

    /** 固定内容段落（可缓存）。 */
    public static SimplePromptSection cached(String name, String content, int order) {
        return new SimplePromptSection(name, content, order, true);
    }
}
