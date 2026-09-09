package com.cloudai.context.impl;

import com.cloudai.context.model.EnvironmentInfo;
import com.cloudai.context.spi.ContextProvider;
import com.cloudai.core.spi.PromptSection;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 默认环境信息提供者 — 收集 JVM/OS 环境数据并生成 PromptSection。
 *
 * <p>order=30，属于动态段落（每次会话间可能变化）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultEnvironmentProvider implements ContextProvider {

    private final EnvironmentInfo info;

    public DefaultEnvironmentProvider(@Nullable Path workDir) {
        this(workDir, List.of());
    }

    public DefaultEnvironmentProvider(@Nullable Path workDir, List<String> modelNames) {
        this.info = new EnvironmentInfo(
                workDir,
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                System.getProperty("java.version", "unknown"),
                LocalDate.now().toString(),
                ZoneId.systemDefault().toString(),
                modelNames);
    }

    public DefaultEnvironmentProvider(EnvironmentInfo info) {
        this.info = info;
    }

    public EnvironmentInfo info() {
        return info;
    }

    @Override
    public PromptSection buildSection() {
        var sb = new StringBuilder("[Environment]\n");
        if (info.workDir() != null) {
            sb.append("Working directory: ").append(info.workDir()).append("\n");
        }
        sb.append("OS: ").append(info.osName()).append(" (").append(info.osArch()).append(")\n");
        sb.append("Java: ").append(info.javaVersion()).append("\n");
        sb.append("Date: ").append(info.currentDate()).append(" (").append(info.timezone()).append(")");
        if (!info.modelNames().isEmpty()) {
            sb.append("\nAvailable models: ").append(String.join(", ", info.modelNames()));
        }
        return new PromptSection() {
            @Override public String name() { return "Environment"; }
            @Override public String content() { return sb.toString(); }
            @Override public int order() { return 30; }
        };
    }
}
