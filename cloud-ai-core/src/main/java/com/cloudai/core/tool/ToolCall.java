package com.cloudai.core.tool;

import org.jspecify.annotations.Nullable;

/**
 * 工具调用（来自模型响应）。
 *
 * @param id        调用 ID
 * @param name      工具名称
 * @param arguments 参数 JSON 字符串
  * @author cloud-ai
 * @since 1.0
*/
public record ToolCall(@Nullable String id, @Nullable String name, @Nullable String arguments) {
    public ToolCall {
        if (id == null) id = "";
        if (name == null) name = "";
        if (arguments == null) arguments = "";
    }

    /** 流式聚合用：是否为完整 ToolCall（非增量 chunk）  * @author cloud-ai
 * @since 1.0
*/
    public boolean hasIdentity() {
        return !id.isEmpty() && !name.isEmpty();
    }
}
