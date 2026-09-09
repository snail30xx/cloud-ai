package com.cloudai.execution.spi;

import com.cloudai.core.model.ToolDefinition;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 工具注册表 SPI — 管理可用工具的发现和注册。
 *
 * <p>运行时通过 {@link #find(String)} 获取执行器，
 * 通过 {@link #listDefinitions()} 获取工具定义（用于发送给 LLM）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface ToolRegistry {

    /**
     * 根据工具名查找执行器。
     *
     * @param toolName 工具名
     * @return 执行器，未找到时返回 null
     */
    @Nullable ToolExecutor find(String toolName);

    /**
     * 列出所有已注册工具的定义（用于发送给 LLM）。
     *
     * @return 工具定义列表
     */
    List<ToolDefinition> listDefinitions();

    /**
     * 注册工具。
     *
     * @param definition 工具定义
     * @param executor   执行器
     */
    void register(ToolDefinition definition, ToolExecutor executor);
}
