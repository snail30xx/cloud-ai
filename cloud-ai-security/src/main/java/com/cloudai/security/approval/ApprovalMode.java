package com.cloudai.security.approval;

/**
 * 审批模式 — 控制哪些操作需要人工审批。
 *
 * <ul>
 *   <li>{@link #AUTO}：低风险自动放行，中高风险等待审批（默认）</li>
 *   <li>{@link #MANUAL}：所有操作（含低风险）均等待人工审批</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public enum ApprovalMode {

    AUTO,

    MANUAL
}
