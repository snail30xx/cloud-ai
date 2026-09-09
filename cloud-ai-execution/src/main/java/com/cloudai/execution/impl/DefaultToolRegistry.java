package com.cloudai.execution.impl;

import com.cloudai.core.model.ToolDefinition;
import com.cloudai.execution.spi.ToolExecutor;
import com.cloudai.execution.spi.ToolRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认工具注册表 — 基于 ConcurrentHashMap 的内存注册表。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultToolRegistry implements ToolRegistry {
    private static final Logger log = LoggerFactory.getLogger(DefaultToolRegistry.class);

    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();

    @Override
    public @Nullable ToolExecutor find(String toolName) {
        var entry = tools.get(toolName);
        return entry != null ? entry.executor() : null;
    }

    @Override
    public List<ToolDefinition> listDefinitions() {
        var defs = new ArrayList<ToolDefinition>(tools.size());
        tools.forEach((name, entry) -> defs.add(entry.definition()));
        return List.copyOf(defs);
    }

    @Override
    public void register(ToolDefinition definition, ToolExecutor executor) {
        tools.put(definition.name(), new ToolEntry(definition, executor));
        log.info("Tool registered: {}", definition.name());
    }

    /** 已注册工具数量。 */
    public int size() {
        return tools.size();
    }

    private record ToolEntry(ToolDefinition definition, ToolExecutor executor) {}
}
