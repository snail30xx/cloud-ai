package com.cloudai.runtime.config;

import com.cloudai.runtime.model.AgentType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 运行时配置属性 — 绑定 {@code cloud-ai.agent} 命名空间。
 *
 * @param maxTurns     最大 LLM 调用轮次
 * @param timeout      单次运行超时
 * @param type         Agent 循环类型
 * @param maxPlanSteps 规划阶段最大步数（仅 PLAN_THEN_EXECUTE 生效）
 * @author cloud-ai
 * @since 1.0
 */
@ConfigurationProperties("cloud-ai.agent")
public record RuntimeProperties(int maxTurns, Duration timeout, AgentType type, int maxPlanSteps) {

    public RuntimeProperties() {
        this(50, Duration.ofMinutes(10), AgentType.REACT, 10);
    }

    public RuntimeProperties {
        if (maxTurns <= 0) {
            maxTurns = 50;
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofMinutes(10);
        }
        if (type == null) {
            type = AgentType.REACT;
        }
        if (maxPlanSteps <= 0) {
            maxPlanSteps = 10;
        }
    }
}