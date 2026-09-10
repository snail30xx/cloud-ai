package com.cloudai.llm.retry;

import com.cloudai.llm.exception.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * LLM 调用重试工具类 — 不依赖 Spring Retry。
 *
 * <p>指数退避策略：
 * <ul>
 *   <li>初始间隔：2 秒</li>
 *   <li>乘数：5x</li>
 *   <li>最大间隔：3 分钟</li>
 * </ul>
 *
 * <p>仅当异常 {@link LlmException#isRetryable()} 返回 true 时才重试。
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

    private RetryUtils() {}

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
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            try {
                return callable.get();
            } catch (Exception e) {
                lastException = e;
                if (!isRetryable(e)) {
                    if (e instanceof LlmException le) {
                        throw le;
                    }
                    throw new RuntimeException("Unexpected error during retry execution", e);
                }
                attempt++;
                if (attempt < maxRetries) {
                    long backoff = calculateBackoff(attempt);
                    log.warn("LLM call to '{}' failed (attempt {}) — retrying in {}ms...",
                            provider, attempt, backoff);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            }
        }

        if (lastException instanceof LlmException le) {
            throw le;
        }
        throw new RuntimeException("Unexpected error during retry execution", lastException);
    }

    /** 计算第 n 次重试的退避时间。 */
    private static long calculateBackoff(int attempt) {
        long backoff = (long) (INITIAL_INTERVAL_MS * Math.pow(MULTIPLIER, attempt - 1));
        return Math.min(backoff, MAX_INTERVAL_MS);
    }

    /** 判断异常是否可重试。 */
    private static boolean isRetryable(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof LlmException le && !le.isRetryable()) {
                return false;
            }
            if (cause instanceof LlmException le && le.isRetryable()) {
                return true;
            }
            cause = cause.getCause();
        }
        return true;
    }

    /**
     * 创建 Reactor Retry 规格，用于流式调用的重试。
     *
     * <p>仅当异常 {@link LlmException#isRetryable()} 返回 true 时才重试。
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