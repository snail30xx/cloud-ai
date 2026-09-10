package com.cloudai.runtime;

/**
 * Agent 循环类型。
 *
 * @author cloud-ai
 * @since 1.0
 */
public enum AgentType {
    /** ReAct 模式：推理与工具调用交替进行 */
    REACT,
    /** Plan-then-Execute 模式：先规划全部工具调用，再执行，最后综合 */
    PLAN_THEN_EXECUTE
}