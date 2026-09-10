package com.cloudai.execution.builtin;

import com.cloudai.core.tool.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileWriteExecutor")
class FileWriteExecutorTest {

    private final FileWriteExecutor executor = new FileWriteExecutor();

    private static String jsonArgs(Path path, String content) {
        return "{\"path\":\"" + path.toString().replace('\\', '/')
                + "\",\"content\":\"" + content + "\"}";
    }

    @Nested
    @DisplayName("successful writes")
    class SuccessWrites {
        @Test
        @DisplayName("should write content to new file")
        void shouldWriteNewFile(@TempDir Path tempDir) throws Exception {
            var file = tempDir.resolve("output.txt");

            var result = executor.execute(
                    new ToolCall("call_1", "file_write", jsonArgs(file, "hello")));

            assertTrue(result.success(), result.error());
            assertEquals("hello", Files.readString(file));
        }

        @Test
        @DisplayName("should create parent directories")
        void shouldCreateParentDirs(@TempDir Path tempDir) throws Exception {
            var file = tempDir.resolve("a/b/c/output.txt");

            var result = executor.execute(
                    new ToolCall("call_1", "file_write", jsonArgs(file, "nested")));

            assertTrue(result.success(), result.error());
            assertEquals("nested", Files.readString(file));
        }

        @Test
        @DisplayName("should overwrite existing file")
        void shouldOverwriteExisting(@TempDir Path tempDir) throws Exception {
            var file = tempDir.resolve("existing.txt");
            Files.writeString(file, "old content");

            executor.execute(
                    new ToolCall("call_1", "file_write", jsonArgs(file, "new content")));

            assertEquals("new content", Files.readString(file));
        }
    }

    @Nested
    @DisplayName("failure cases")
    class FailureCases {
        @Test
        @DisplayName("should fail when path missing")
        void shouldFailWhenPathMissing() {
            var result = executor.execute(
                    new ToolCall("call_1", "file_write", "{\"content\":\"hello\"}"));

            assertFalse(result.success());
            assertTrue(result.error().contains("path"));
        }

        @Test
        @DisplayName("should fail when content missing")
        void shouldFailWhenContentMissing(@TempDir Path tempDir) {
            var path = tempDir.resolve("x.txt").toString().replace('\\', '/');
            var result = executor.execute(
                    new ToolCall("call_1", "file_write", "{\"path\":\"" + path + "\"}"));

            assertFalse(result.success());
            assertTrue(result.error().contains("content"));
        }
    }
}
