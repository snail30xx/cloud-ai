package com.cloudai.security.spi;

import com.cloudai.core.model.ToolCall;
import com.cloudai.security.model.PermissionResult;
import com.cloudai.security.model.SecurityContext;

/**
 * 权限管理器 — 校验 Agent 工具调用是否被允许。
 *
 * <p>核心 SPI，在工具执行前调用。默认实现应遵循"安全优先"原则（拒绝所有）。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@FunctionalInterface
public interface PermissionManager {

    /**
     * 校验工具调用权限。
     *
     * @param toolCall 待执行的工具调用
     * @param context  安全上下文
     * @return 权限决策结果
     */
    PermissionResult check(ToolCall toolCall, SecurityContext context);
}