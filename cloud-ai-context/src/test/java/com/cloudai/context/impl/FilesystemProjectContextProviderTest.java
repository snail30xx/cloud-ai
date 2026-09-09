package com.cloudai.context.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FilesystemProjectContextProvider")
class FilesystemProjectContextProviderTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Correct path")
    class Correct {

        @Test
        @DisplayName("读取 AGENTS.md")
        void readsAgentsMd() throws IOException {
            Files.writeString(tempDir.resolve("AGENTS.md"), "# Project Rules\nDo not commit secrets.");
            var provider = new FilesystemProjectContextProvider(tempDir);
            var section = provider.buildSection();
            assertTrue(section.content().contains("Project Rules"));
            assertTrue(section.content().contains("Do not commit secrets."));
            assertEquals(40, section.order());
        }

        @Test
        @DisplayName("读取 CLAUDE.md")
        void readsClaudeMd() throws IOException {
            Files.writeString(tempDir.resolve("CLAUDE.md"), "# Claude Instructions\nUse Java 21.");
            var provider = new FilesystemProjectContextProvider(tempDir);
            var section = provider.buildSection();
            assertTrue(section.content().contains("Claude Instructions"));
        }

        @Test
        @DisplayName("同时读取 AGENTS.md 和 CLAUDE.md")
        void readsBoth() throws IOException {
            Files.writeString(tempDir.resolve("AGENTS.md"), "agents content");
            Files.writeString(tempDir.resolve("CLAUDE.md"), "claude content");
            var provider = new FilesystemProjectContextProvider(tempDir);
            var section = provider.buildSection();
            assertTrue(section.content().contains("agents content"));
            assertTrue(section.content().contains("claude content"));
        }
    }

    @Nested
    @DisplayName("Border / Error")
    class BorderError {

        @Test
        @DisplayName("文件不存在时返回空段落")
        void noFilesReturnsEmpty() {
            var provider = new FilesystemProjectContextProvider(tempDir);
            var section = provider.buildSection();
            assertTrue(section.content().isEmpty());
        }
    }
}
