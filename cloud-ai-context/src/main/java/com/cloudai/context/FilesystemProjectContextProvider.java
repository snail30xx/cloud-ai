package com.cloudai.context;

import com.cloudai.context.ContextProvider;
import com.cloudai.core.prompt.PromptSection;
import com.cloudai.core.prompt.SimplePromptSection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件系统项目上下文提供者 — 从工作目录读取 AGENTS.md 和 CLAUDE.md。
 *
 * <p>读取顺序：AGENTS.md → CLAUDE.md（如果都存在则拼接）。
 * 文件不存在时返回空 content，由 PromptAssembler 自动跳过。</p>
 *
 * <p>order=40，属于动态段落。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class FilesystemProjectContextProvider implements ContextProvider {

    private static final Logger log = LoggerFactory.getLogger(FilesystemProjectContextProvider.class);

    private static final List<String> CONTEXT_FILES = List.of("AGENTS.md", "CLAUDE.md");

    private final Path workDir;

    public FilesystemProjectContextProvider(@Nullable Path workDir) {
        this.workDir = workDir != null ? workDir : Path.of(".");
    }

    @Override
    public PromptSection buildSection() {
        var sb = new StringBuilder();
        for (var fileName : CONTEXT_FILES) {
            var content = readFile(workDir.resolve(fileName));
            if (content != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(content.strip());
            }
        }
        if (sb.isEmpty()) {
            return SimplePromptSection.empty("ProjectContext");
        }
        return new PromptSection() {
            @Override public String name() { return "ProjectContext"; }
            @Override public String content() { return sb.toString(); }
            @Override public int order() { return 40; }
        };
    }

    @Nullable
    private String readFile(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                return Files.readString(path);
            }
        } catch (IOException e) {
            log.warn("Failed to read context file {}: {}", path, e.getMessage());
        }
        return null;
    }

    public Path workDir() {
        return workDir;
    }
}
