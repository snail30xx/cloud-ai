package com.cloudai.context.config;


import java.nio.file.Path;

/**
 * 上下文层配置属性。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ContextProperties {

    /**
     * 工作目录路径，默认为当前 JVM 工作目录。
     */
    private Path workDir = Path.of(".");

    /**
     * 是否启用 AGENTS.md / CLAUDE.md 读取。
     */
    private boolean projectContextEnabled = true;

    public Path getWorkDir() {
        return workDir;
    }

    public void setWorkDir(Path workDir) {
        this.workDir = workDir;
    }

    public boolean isProjectContextEnabled() {
        return projectContextEnabled;
    }

    public void setProjectContextEnabled(boolean projectContextEnabled) {
        this.projectContextEnabled = projectContextEnabled;
    }
}
