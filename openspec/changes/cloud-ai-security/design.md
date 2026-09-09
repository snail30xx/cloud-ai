# cloud-ai-security — Design

## 架构决策

### 1. 分层：SPI 在 core，实现在 security

**决策**：将 SPI 接口和模型定义放在安全模块自身，不放入 `cloud-ai-core`。

**理由**：
- `ChatModel` 是 LLM 调用核心，所有上层模块都依赖它，放在 core 合理
- 安全 SPI 只有 `cloud-ai-runtime` 和 `cloud-ai-execution` 需要，放在安全模块避免 core 膨胀
- 安全模块可独立演进，不强制所有模块依赖安全

**对比 Spring AI**：Spring AI 没有独立的 Security 模块，安全能力通过 Advisor 散布在 `spring-ai-client-chat` 中。

### 2. 接口设计：面向工具调用，而非通用 RBAC

**决策**：围绕 `ToolCall` 对象设计权限校验，而非通用 RBAC 模型。

**理由**：
- 这是 Agent 工具调用的安全底座，不是用户管理系统
- `PermissionManager.check(ToolCall, SecurityContext)` 直接回答 "这个工具调用是否允许"
- 外部可以在此之上构建 RBAC/ABAC，但安全模块本身保持简单

**参考 Spring AI**：Spring AI 的 Advisor 拦截也是面向工具调用的，而非通用权限模型。

### 3. ApprovalGateway：同步阻塞式审批

**决策**：审批接口是同步的（`requestApproval` 返回 `ApprovalResponse`），而非异步回调。

**理由**：
- Agent 循环需要等待审批结果才能继续
- 简化调用方逻辑
- 默认实现的超时机制保证不会无限阻塞

**参考 Spring AI**：Spring AI 没有内置审批机制，依赖外部系统。

### 4. AuditLogger：接口 + SLF4J 默认实现

**决策**：审计日志接口不绑定特定存储，默认实现使用 SLF4J。

**理由**：
- SLF4J 是 Java 生态标准，任何日志框架都能接入
- 通过 `SecurityAutoConfiguration` 的 `@ConditionalOnMissingBean` 支持替换
- 结构化日志（`AuditEvent` record）方便 ELK/Splunk 解析

## 模块结构

```
cloud-ai-security/
├── pom.xml
└── src/main/java/com/cloudai/security/
    ├── spi/
    │   ├── PermissionManager.java     # 权限校验 SPI
    │   ├── ApprovalGateway.java       # 审批网关 SPI
    │   └── AuditLogger.java           # 审计日志 SPI
    ├── model/
    │   ├── PermissionResult.java      # 权限决策结果
    │   ├── ApprovalRequest.java       # 审批请求
    │   ├── ApprovalResponse.java      # 审批响应
    │   ├── AuditEvent.java            # 审计事件
    │   ├── SecurityContext.java       # 安全上下文
    │   └── OperationType.java         # 操作类型枚举
    ├── impl/
    │   ├── DefaultPermissionManager.java   # 默认：拒绝所有（安全优先）
    │   ├── InMemoryApprovalGateway.java    # 默认：内存审批（超时自动拒绝）
    │   └── Slf4jAuditLogger.java           # 默认：SLF4J 日志
    └── config/
        └── SecurityAutoConfiguration.java  # 自动装配
```

## 接口设计

### PermissionManager

```java
@FunctionalInterface
public interface PermissionManager {
    PermissionResult check(ToolCall toolCall, SecurityContext context);
}
```

设计要点：
- `@FunctionalInterface`：允许 lambda 注册，与 DSH 的 `Advisor` 接口一致
- `ToolCall`：来自 `cloud-ai-core`，包含 `name`、`arguments`
- `SecurityContext`：包含 `agentId`、`userId`、`sessionId`，可扩展
- `PermissionResult`：`allowed` + `reason`，布尔值 + 可读原因

### ApprovalGateway

```java
public interface ApprovalGateway {
    ApprovalResponse requestApproval(ApprovalRequest request);
}
```

设计要点：
- 同步接口（非 `@FunctionalInterface`，审批逻辑复杂不宜 lambda）
- `ApprovalRequest`：`operation` + `risk` + `context`
- 风险等级：`LOW`（自动放行）、`MEDIUM`（需审批）、`HIGH`（需审批 + 原因）
- 默认实现超时 30s 自动拒绝

### AuditLogger

```java
public interface AuditLogger {
    void logAccess(AuditEvent event);
    void logDecision(AuditEvent event);
    void logExecution(AuditEvent event);
}
```

设计要点：
- 三个方法对应三个审计阶段：访问 → 决策 → 执行
- `AuditEvent` 包含完整上下文：时间戳、操作类型、目标、结果、元数据
- 默认实现使用 SLF4J 分级：`logAccess` DEBUG，`logDecision` INFO，`logExecution` INFO

## 与现有模块的对比

| 设计维度 | cloud-ai-llm | cloud-ai-security |
|----------|-------------|-------------------|
| SPI 位置 | core 中（`ChatModel`） | 自身模块中 |
| 模型类型 | record | record |
| 默认实现 | `AbstractLlmAdapter` | `DefaultPermissionManager` 等 |
| 自动装配 | `LlmAutoConfiguration` | `SecurityAutoConfiguration` |
| 条件装配 | `@ConditionalOnProperty("cloud-ai.llm.enabled")` | `@ConditionalOnProperty("cloud-ai.security.enabled")` |
| 扩展点 | 模板方法继承 | 接口替换 + `@ConditionalOnMissingBean` |

## 与 Spring AI 的差异

| 维度 | Spring AI | DSH cloud-ai-security |
|------|-----------|----------------------|
| **安全模型** | Advisor 链拦截 | 独立的三层 SPI（Permission + Approval + Audit） |
| **权限控制** | 分散在多个 Advisor 中 | 集中在 `PermissionManager` |
| **审批机制** | 无内置 | `ApprovalGateway` 同步审批 |
| **审计能力** | 靠日志框架 | `AuditLogger` 结构化审计 |
| **扩展方式** | 自定义 Advisor | 替换 SPI 实现 Bean |
| **复杂度** | 高（需理解 Advisor 链 + Order） | 低（3 个接口，职责清晰） |