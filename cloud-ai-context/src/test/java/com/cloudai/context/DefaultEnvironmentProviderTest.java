package com.cloudai.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DefaultEnvironmentProvider")
class DefaultEnvironmentProviderTest {

    @Nested
    @DisplayName("Correct path")
    class Correct {

        @Test
        @DisplayName("生成包含 OS 和 Java 版本的段落")
        void generatesEnvSection() {
            var provider = new DefaultEnvironmentProvider(Path.of("/tmp"));
            var section = provider.buildSection();
            assertEquals("Environment", section.name());
            assertEquals(30, section.order());
            assertTrue(section.content().contains("OS:"));
            assertTrue(section.content().contains("Java:"));
            assertTrue(section.content().contains("tmp"));
        }

        @Test
        @DisplayName("包含日期和时区")
        void includesDateAndTimezone() {
            var provider = new DefaultEnvironmentProvider((java.nio.file.Path) null);
            var section = provider.buildSection();
            assertTrue(section.content().contains("Date:"));
            assertTrue(section.content().contains("("));
        }

        @Test
        @DisplayName("包含模型名列表")
        void includesModelNames() {
            var provider = new DefaultEnvironmentProvider(null, List.of("gpt-4o", "deepseek-v4"));
            var section = provider.buildSection();
            assertTrue(section.content().contains("gpt-4o"));
            assertTrue(section.content().contains("deepseek-v4"));
        }
    }

    @Nested
    @DisplayName("Border")
    class Border {

        @Test
        @DisplayName("workDir 为 null 时不输出工作目录行")
        void nullWorkDirOmitsLine() {
            var provider = new DefaultEnvironmentProvider((java.nio.file.Path) null);
            var section = provider.buildSection();
            assertTrue(!section.content().contains("Working directory:"));
        }
    }
}
