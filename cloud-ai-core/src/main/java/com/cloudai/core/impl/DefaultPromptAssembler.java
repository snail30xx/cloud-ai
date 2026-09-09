package com.cloudai.core.impl;

import com.cloudai.core.spi.PromptAssembler;
import com.cloudai.core.spi.PromptSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 默认提示词组装器 — 按 order 升序排列所有段落，用双换行连接。
 *
 * <p>空内容的段落自动跳过。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultPromptAssembler implements PromptAssembler {

    @Override
    public String assemble(List<PromptSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        var sorted = new ArrayList<>(sections);
        sorted.sort(Comparator.comparingInt(PromptSection::order));

        var sb = new StringBuilder();
        for (var section : sorted) {
            var content = section.content();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(content);
        }
        return sb.toString();
    }
}
