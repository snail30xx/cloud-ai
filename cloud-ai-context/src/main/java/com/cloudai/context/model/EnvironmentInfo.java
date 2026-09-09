package com.cloudai.context.model;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * 环境信息 — 运行时环境的上下文数据。
 *
 * @param workDir      工作目录，null 表示当前 JVM 工作目录
 * @param osName       操作系统名称
 * @param osArch       操作系统架构
 * @param javaVersion  Java 版本
 * @param currentDate  当前日期（ISO 格式）
 * @param timezone     时区
 * @param modelNames   已注册的模型名称列表
 * @author cloud-ai
 * @since 1.0
 */
public record EnvironmentInfo(
        @Nullable Path workDir,
        String osName,
        String osArch,
        String javaVersion,
        String currentDate,
        String timezone,
        List<String> modelNames) {

    public EnvironmentInfo {
        modelNames = modelNames != null ? List.copyOf(modelNames) : List.of();
    }
}
