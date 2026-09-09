package com.cloudai.security.model;

/**
 * 风险等级枚举。
 *
 * @author cloud-ai
 * @since 1.0
 */
public enum RiskLevel {
    /** 低风险：只读操作，自动放行 */
    LOW,
    /** 中风险：写入操作，需要审批 */
    MEDIUM,
    /** 高风险：执行命令/删除，需要审批 + 原因 */
    HIGH;

    /**
     * 根据操作类型自动评估风险等级。
     *
     * <p>默认映射：
     * <ul>
     *   <li>{@link OperationType#FILE_READ} → {@link #LOW}</li>
     *   <li>{@link OperationType#FILE_WRITE}、{@link OperationType#NETWORK_CALL} → {@link #MEDIUM}</li>
     *   <li>{@link OperationType#FILE_DELETE}、{@link OperationType#SHELL_EXEC}、{@link OperationType#CUSTOM} → {@link #HIGH}</li>
     * </ul>
     *
     * @param operationType 操作类型
     * @return 风险等级
     */
    public static RiskLevel from(OperationType operationType) {
        if (operationType == null) {
            return HIGH;
        }
        return switch (operationType) {
            case FILE_READ -> LOW;
            case FILE_WRITE, NETWORK_CALL -> MEDIUM;
            case FILE_DELETE, SHELL_EXEC, CUSTOM -> HIGH;
        };
    }
}
