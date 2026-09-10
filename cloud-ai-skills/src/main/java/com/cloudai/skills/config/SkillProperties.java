package com.cloudai.skills.config;


import java.nio.file.Path;

/**
 * 技能层配置属性。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class SkillProperties {

    private Path workDir = Path.of(".");
    private boolean enabled = true;

    public Path getWorkDir() {
        return workDir;
    }

    public void setWorkDir(Path workDir) {
        this.workDir = workDir;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
