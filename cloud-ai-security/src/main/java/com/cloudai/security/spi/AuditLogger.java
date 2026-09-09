package com.cloudai.security.spi;

import com.cloudai.security.model.AuditEvent;

/**
 * 审计日志 — 记录 Agent 操作的全链路信息。
 *
 * <p>分三个阶段记录：
 * <ul>
 *   <li>{@link #logAccess} — 访问记录（谁、什么时间、调了什么）</li>
 *   <li>{@link #logDecision} — 决策记录（权限/审批是否通过）</li>
 *   <li>{@link #logExecution} — 执行记录（成功/失败、耗时、返回值摘要）</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface AuditLogger {

    /** 记录访问事件（DEBUG 级别）。 */
    void logAccess(AuditEvent event);

    /** 记录决策事件（拒绝时 WARN 级别）。 */
    void logDecision(AuditEvent event);

    /** 记录执行结果（失败时 ERROR 级别）。 */
    void logExecution(AuditEvent event);
}