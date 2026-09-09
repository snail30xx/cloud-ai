# cloud-ai-security — Tasks

## Phase 1: 模型层（无外部依赖，可并行）

- [x] **Task 1.1**: 创建 `OperationType` 枚举
  - 文件：`cloud-ai-security/src/main/java/com/cloudai/security/model/OperationType.java`
  - 内容：`FILE_READ`、`FILE_WRITE`、`FILE_DELETE`、`SHELL_EXEC`、`NETWORK_CALL`、`CUSTOM`
- 验证：编译通过

- [x] **Task 1.2**: 创建 `SecurityContext` record
  - 字段：`agentId`、`userId`、`sessionId`、`metadata(Map)`
- 验证：`mvn test -pl cloud-ai-security`

- [x] **Task 1.3**: 创建 `PermissionResult` record
  - 字段：`allowed(boolean)`、`reason(String)`
  - 静态工厂：`allow()`、`deny(String reason)`
- 验证：单元测试覆盖 `allow()` 和 `deny()`

- [x] **Task 1.4**: 创建 `ApprovalRequest` + `ApprovalResponse` record
  - `ApprovalRequest`：`operation(String)`、`risk(RiskLevel)`、`context(Map)`、`timeout(Duration)`
  - `ApprovalResponse`：`approved(boolean)`、`reason(String)`、`approver(nullable)`
  - `RiskLevel` 枚举：`LOW`、`MEDIUM`、`HIGH`
- 验证：单元测试覆盖三种风险等级

- [x] **Task 1.5**: 创建 `AuditEvent` record
  - 字段：`agentId`、`operation`、`target`、`result`、`timestamp`、`metadata(Map)`
  - 静态工厂：`AuditEvent.of(...)`
- 验证：单元测试

## Phase 2: SPI 接口（依赖 Phase 1 模型）

- [x] **Task 2.1**: 创建 `PermissionManager` 接口
  - 方法：`PermissionResult check(ToolCall toolCall, SecurityContext context)`
  - 标注 `@FunctionalInterface`
- 文件：`cloud-ai-security/src/main/java/com/cloudai/security/spi/PermissionManager.java`

- [x] **Task 2.2**: 创建 `ApprovalGateway` 接口
  - 方法：`ApprovalResponse requestApproval(ApprovalRequest request)`
- 文件：`cloud-ai-security/src/main/java/com/cloudai/security/spi/ApprovalGateway.java`

- [x] **Task 2.3**: 创建 `AuditLogger` 接口
  - 方法：`logAccess(AuditEvent)`、`logDecision(AuditEvent)`、`logExecution(AuditEvent)`
- 文件：`cloud-ai-security/src/main/java/com/cloudai/security/spi/AuditLogger.java`

## Phase 3: 默认实现（依赖 Phase 2 SPI）

- [x] **Task 3.1**: 实现 `DefaultPermissionManager`
  - 基于 `List<PermissionRule>` 的白名单/黑名单匹配
  - 支持 Ant 风格路径匹配（`/workspace/**`）
  - 默认拒绝所有
- 验证：单元测试覆盖白名单匹配、黑名单覆盖、路径通配符、空规则

- [x] **Task 3.2**: 实现 `InMemoryApprovalGateway`
  - `LOW` 风险自动放行
  - `MEDIUM`/`HIGH` 风险使用 `CountDownLatch` 等待审批
  - 超时自动拒绝（默认 30s）
  - 提供 `approve(requestId)` / `deny(requestId)` 方法供外部调用
- 验证：单元测试覆盖自动放行、审批通过、审批拒绝、超时拒绝

- [x] **Task 3.3**: 实现 `Slf4jAuditLogger`
  - `logAccess`：DEBUG 级别
  - `logDecision`：INFO 级别（denied 时 WARN）
  - `logExecution`：INFO 级别（失败时 ERROR）
  - 结构化 JSON 输出
- 验证：单元测试覆盖三种日志级别

## Phase 4: 自动装配（依赖 Phase 3 实现）

- [x] **Task 4.1**: 创建 `SecurityAutoConfiguration`
  - `@ConditionalOnProperty("cloud-ai.security.enabled")`，默认 true
  - 三个 `@Bean` + `@ConditionalOnMissingBean`
- 文件：`cloud-ai-security/src/main/java/com/cloudai/security/config/SecurityAutoConfiguration.java`

- [x] **Task 4.2**: 添加 `SecurityProperties` 配置类
  - `cloud-ai.security.enabled`（默认 true）
  - `cloud-ai.security.approval.timeout`（默认 30s）
- 文件：`cloud-ai-security/src/main/java/com/cloudai/security/config/SecurityProperties.java`

## Phase 5: 集成测试

- [x] **Task 5.1**: 验证自动装配
  - 测试容器启动后三个 Bean 自动创建
  - 测试 `@ConditionalOnMissingBean` 替换逻辑
- 测试 `enabled=false` 禁用模块

- [x] **Task 5.2**: 验证完整链路
  - `SecurityContext → PermissionManager → AuditLogger` 集成测试
  - `ApprovalGateway` 审批流程集成测试

---

**总计：约 15 个 Java 文件，14 个测试类**
**预估工期：Phase 1-4 为核心实现，Phase 5 为验证**
