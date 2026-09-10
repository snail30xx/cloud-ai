package com.cloudai.execution.config;


import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 执行模块配置属性 — 绑定 {@code cloud-ai.execution} 命名空间。
 *
 * @param fileReadEnabled  是否启用 file_read 工具
 * @param fileWriteEnabled 是否启用 file_write 工具
 * @param shellEnabled     是否启用 shell_exec 工具
 * @param shellTimeout     shell_exec 单条命令超时
 * @param workspace        内置文件/Shell 工具的工作目录（相对路径以其为基准），null 表示不限制
 * @author cloud-ai
 * @since 1.0
 */
public record ExecutionProperties(boolean fileReadEnabled,
                                   boolean fileWriteEnabled,
                                   boolean shellEnabled,
                                   @Nullable Duration shellTimeout,
                                   @Nullable Path workspace) {

    public ExecutionProperties {
        if (shellTimeout == null || shellTimeout.isNegative() || shellTimeout.isZero()) {
            shellTimeout = Duration.ofSeconds(30);
        }
    }

    public ExecutionProperties() {
        this(true, true, true, null, null);
    }
}
