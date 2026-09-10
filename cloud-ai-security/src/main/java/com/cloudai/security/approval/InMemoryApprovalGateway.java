package com.cloudai.security.approval;

import com.cloudai.security.approval.ApprovalRequest;
import com.cloudai.security.approval.ApprovalResponse;
import com.cloudai.security.RiskLevel;
import com.cloudai.security.approval.ApprovalGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 内存审批网关 — 低风险自动放行，中高风险等待审批。
 *
 * <p>提供 {@link #approve(String, String)} / {@link #deny(String, String)} 方法供外部审批。</p>
 * <p>使用 {@link #listPending()} 获取当前待审批请求及其 ID。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class InMemoryApprovalGateway implements ApprovalGateway {
    private static final Logger log = LoggerFactory.getLogger(InMemoryApprovalGateway.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final Duration defaultTimeout;
    private final Map<String, PendingApproval> pending = new ConcurrentHashMap<>();

    /** 使用默认超时（30s）创建。 */
    public InMemoryApprovalGateway() {
        this(DEFAULT_TIMEOUT);
    }

    /** 指定默认超时创建。 */
    public InMemoryApprovalGateway(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : DEFAULT_TIMEOUT;
    }

    /** 返回当前待审批请求及其 ID，供外部审批系统发现。 */
    public Map<String, ApprovalRequest> listPending() {
        var result = new java.util.LinkedHashMap<String, ApprovalRequest>();
        pending.forEach((id, pa) -> result.put(id, pa.request));
        return result;
    }

    @Override
    public ApprovalResponse requestApproval(ApprovalRequest request) {
        // 低风险自动放行
        if (request.risk() == RiskLevel.LOW) {
            log.debug("Auto-approved low-risk operation: {}", request.operation());
            return ApprovalResponse.approved("Auto-approved: low risk operation");
        }

        var requestId = UUID.randomUUID().toString();
        var latch = new CountDownLatch(1);
        var pendingApproval = new PendingApproval(request, latch);
        pending.put(requestId, pendingApproval);

        log.info("Approval requested: id={}, operation={}, risk={}",
                requestId, request.operation(), request.risk());

        try {
            var timeout = request.timeout() != null ? request.timeout() : defaultTimeout;
            var timeoutMs = timeout.toMillis();
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);

            if (!completed) {
                log.warn("Approval timeout: id={}, operation={}", requestId, request.operation());
                pending.remove(requestId);
                return ApprovalResponse.timeout();
            }

            var result = pendingApproval.result;
            pending.remove(requestId);
            log.info("Approval resolved: id={}, approved={}, by={}",
                    requestId, result.approved(), result.approver());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(requestId);
            log.warn("Approval interrupted: id={}, operation={}", requestId, request.operation());
            return ApprovalResponse.denied("Approval interrupted");
        }
    }

    /** 批准指定的审批请求。 */
    public boolean approve(String requestId, String approver) {
        var pendingApproval = pending.get(requestId);
        if (pendingApproval == null) {
            log.warn("Approval request not found: {}", requestId);
            return false;
        }
        pendingApproval.result = ApprovalResponse.approved(
                "Approved by " + approver, approver);
        pendingApproval.latch.countDown();
        return true;
    }

    /** 拒绝指定的审批请求。 */
    public boolean deny(String requestId, String reason) {
        var pendingApproval = pending.get(requestId);
        if (pendingApproval == null) {
            log.warn("Approval request not found: {}", requestId);
            return false;
        }
        pendingApproval.result = ApprovalResponse.denied(reason);
        pendingApproval.latch.countDown();
        return true;
    }

    /** 当前待审批数量。 */
    public int pendingCount() {
        return pending.size();
    }

    private static class PendingApproval {
        final ApprovalRequest request;
        final CountDownLatch latch;
        volatile ApprovalResponse result;

        PendingApproval(ApprovalRequest request, CountDownLatch latch) {
            this.request = request;
            this.latch = latch;
        }
    }
}
