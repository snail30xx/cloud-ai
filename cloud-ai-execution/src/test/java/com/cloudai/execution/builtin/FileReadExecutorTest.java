package com.cloudai.execution.builtin;

import com.cloudai.core.tool.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileReadExecutor")
class FileReadExecutorTest {

    private final FileReadExecutor executor = new FileReadExecutor();

    private static String jsonPath(Path path) {
        return "{\"path\":\"" + path.toString().replace('\\', '/') + "\"}";
    }

    @Nested
    @DisplayName("successful reads")
    class SuccessReads {
        @Test
        @DisplayName("should read existing file")
        void shouldReadExistingFile(@TempDir Path tempDir) throws Exception {
            var file = tempDir.resolve("data.txt");
            Files.writeString(file, "hello world");

            var result = executor.execute(
                    new ToolCall("call_1", "file_read", jsonPath(file)));

            assertTrue(result.success());
            assertEquals("hello world", result.output());
        }

        @Test
        @DisplayName("should read empty file")
        void shouldReadEmptyFile(@TempDir Path tempDir) throws Exception {
            var file = tempDir.resolve("empty.txt");
            Files.createFile(file);

            var result = executor.execute(
                    new ToolCall("call_1", "file_read", jsonPath(file)));

            assertTrue(result.success());
            assertEquals("", result.output());
        }
    }

    @Nested
    @DisplayName("failure cases")
    class FailureCases {
        @Test
        @DisplayName("should fail when file not found")
        void shouldFailWhenNotFound(@TempDir Path tempDir) {
            var result = executor.execute(
                    new ToolCall("call_1", "file_read", jsonPath(tempDir.resolve("nope.txt"))));

            assertFalse(result.success());
            assertTrue(result.error().contains("not found"));
        }

        @Test
        @DisplayName("should fail when path parameter missing")
        void shouldFailWhenPathMissing() {
            var result = executor.execute(
                    new ToolCall("call_1", "file_read", "{}"));

            assertFalse(result.success());
            assertTrue(result.error().contains("path"));
        }

        @Test
        @DisplayName("should fail when arguments empty")
        void shouldFailWhenArgsEmpty() {
            var result = executor.execute(new ToolCall("call_1", "file_read", ""));

            assertFalse(result.success());
        }

        @Test
        @DisplayName("should fail when path is a directory")
        void shouldFailWhenDirectory(@TempDir Path tempDir) {
            var result = executor.execute(
                    new ToolCall("call_1", "file_read", jsonPath(tempDir)));

            assertFalse(result.success());
            assertTrue(result.error().contains("regular file"));
        }
    }
}
