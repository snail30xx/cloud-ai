package com.cloudai.execution.config;


/**
 * 执行模块配置属性 — 绑定 {@code cloud-ai.execution} 命名空间。
 *
 * @author cloud-ai
 * @since 1.0
 */
public record ExecutionProperties(boolean fileReadEnabled,
                                   boolean fileWriteEnabled,
                                   boolean fileDeleteEnabled,
                                   boolean shellEnabled) {

    public ExecutionProperties() {
        this(true, true, true, true);
    }
}
