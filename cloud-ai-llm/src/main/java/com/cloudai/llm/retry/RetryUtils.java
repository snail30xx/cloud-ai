package com.cloudai.llm.retry;

import com.cloudai.llm.exception.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * LLM 调用重试工具类。
 *
 * <p>指数退避策略（共享，线程安全）：
 * <ul>
 *   <li>初始间隔：2 秒</li>
 *   <li>乘数：5x</li>
 *   <li>最大间隔：3 分钟</li>
 * </ul>
 *
 * <p>仅当异常 {@link LlmException#isRetryable()} 返回 {@code true} 时才重试。
 * 不可重试的异常（如认证失败、客户端错误）会直接抛出。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class RetryUtils {
    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    private static final long INITIAL_INTERVAL_MS = 2_000L;
    private static final double MULTIPLIER = 5.0;
    private static final long MAX_INTERVAL_MS = 180_000L;

    /** 共享的退避策略 — 无状态，线程安全 */
    private static final ExponentialBackOffPolicy SHARED_BACKOFF = createBackOffPolicy();

    /** RetryTemplate 缓存，按 maxRetries 复用 */
    private static final ConcurrentHashMap<Integer, RetryTemplate> TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private RetryUtils() {}

    private static ExponentialBackOffPolicy createBackOffPolicy() {
        var policy = new ExponentialBackOffPolicy();
        policy.setInitialInterval(INITIAL_INTERVAL_MS);
        policy.setMultiplier(MULTIPLIER);
        policy.setMaxInterval(MAX_INTERVAL_MS);
        return policy;
    }

    /**
     * 执行带重试的调用，maxRetries 和 provider 在每次调用时动态传入。
     *
     * @param callable   实际调用
     * @param maxRetries 最大重试次数
     * @param provider   提供商标识（用于日志）
     * @param <T>        返回类型
     * @return 调用结果
     * @throws RuntimeException 如果所有重试均失败
     */
    public static <T> T executeWithRetry(Supplier<T> callable, int maxRetries, String provider) {
        var template = TEMPLATE_CACHE.computeIfAbsent(maxRetries, k -> {
            var t = new RetryTemplate();
            t.setBackOffPolicy(SHARED_BACKOFF);
            t.setRetryPolicy(createRetryPolicy(k));
            return t;
        });
        try {
            return template.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.warn("LLM call to '{}' failed (attempt {}) — retrying...",
                            provider, context.getRetryCount());
                }
                return callable.get();
            });
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error during retry execution", e);
        }
    }

    private static SimpleRetryPolicy createRetryPolicy(int maxRetries) {
        return new SimpleRetryPolicy(maxRetries) {
            @Override
            public boolean canRetry(RetryContext context) {
                Throwable t = context.getLastThrowable();
                if (t == null) return true;
                Throwable cause = t;
                while (cause != null) {
                    if (cause instanceof LlmException le && !le.isRetryable()) {
                        return false;
                    }
                    cause = cause.getCause();
                }
                return super.canRetry(context);
            }
        };
    }

    /**
     * 创建 Reactor Retry 规格，用于流式调用的重试。
     *
     * <p>仅当异常 {@link LlmException#isRetryable()} 返回 {@code true} 时才重试。
     * 指数退避：初始 2s，最大 3min。</p>
     *
     * @param maxRetries 最大重试次数
     * @return Reactor Retry 规格
     */
    public static Retry reactorRetrySpec(int maxRetries) {
        return Retry.backoff(maxRetries, Duration.ofMillis(INITIAL_INTERVAL_MS))
                .maxBackoff(Duration.ofMillis(MAX_INTERVAL_MS))
                .filter(throwable -> {
                    if (throwable instanceof LlmException le) {
                        return le.isRetryable();
                    }
                    if (throwable.getCause() instanceof LlmException le) {
                        return le.isRetryable();
                    }
                    return false;
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    var failure = retrySignal.failure();
                    if (failure instanceof LlmException le) {
                        return le;
                    }
                    if (failure.getCause() instanceof LlmException le) {
                        return le;
                    }
                    return new RuntimeException("Stream retry exhausted: " + failure.getMessage(), failure);
                });
    }
}
