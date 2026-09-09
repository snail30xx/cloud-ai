package com.cloudai.core.impl;

import com.cloudai.core.spi.PromptSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DefaultPromptAssembler")
class DefaultPromptAssemblerTest {

    private final DefaultPromptAssembler assembler = new DefaultPromptAssembler();

    private static PromptSection section(String name, String content, int order) {
        return new PromptSection() {
            @Override public String name() { return name; }
            @Override public String content() { return content; }
            @Override public int order() { return order; }
        };
    }

    @Nested
    @DisplayName("Correct path")
    class Correct {

        @Test
        @DisplayName("按 order 升序拼接段落")
        void assemblesByOrder() {
            var sections = List.of(
                    section("Memory", "remember X", 60),
                    section("Persona", "you are assistant", 10),
                    section("Env", "cwd=/tmp", 30)
            );
            var result = assembler.assemble(sections);
            assertEquals("you are assistant\n\ncwd=/tmp\n\nremember X", result);
        }

        @Test
        @DisplayName("相同 order 的段落保持稳定排序")
        void stableOrderForSamePriority() {
            var sections = List.of(
                    section("B", "b-content", 30),
                    section("A", "a-content", 30),
                    section("C", "c-content", 30)
            );
            var result = assembler.assemble(sections);
            assertEquals("b-content\n\na-content\n\nc-content", result);
        }
    }

    @Nested
    @DisplayName("Border / Error")
    class BorderError {

        @Test
        @DisplayName("空列表返回空字符串")
        void emptyList() {
            assertEquals("", assembler.assemble(List.of()));
        }

        @Test
        @DisplayName("null 列表返回空字符串")
        void nullList() {
            assertEquals("", assembler.assemble(null));
        }

        @Test
        @DisplayName("空内容段落被跳过")
        void skipsBlankContent() {
            var sections = List.of(
                    section("A", "keep", 10),
                    section("B", "", 20),
                    section("C", "   ", 30),
                    section("D", "also keep", 40)
            );
            var result = assembler.assemble(sections);
            assertEquals("keep\n\nalso keep", result);
        }
    }
}
