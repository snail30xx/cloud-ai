package com.cloudai.security.spi;

import com.cloudai.security.model.ApprovalRequest;
import com.cloudai.security.model.ApprovalResponse;

/**
 * 审批网关 — 对高危操作进行人工审批。
 *
 * <p>同步接口：调用方阻塞等待审批结果。默认实现提供超时自动拒绝机制。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface ApprovalGateway {

    /**
     * 提交审批请求，同步等待决策。
     *
     * <p>低风险操作应自动放行，无需人工介入。</p>
     *
     * @param request 审批请求
     * @return 审批响应
     */
    ApprovalResponse requestApproval(ApprovalRequest request);
}