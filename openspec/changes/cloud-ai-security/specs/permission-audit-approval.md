# cloud-ai-security — Behavioral Specs

---

## PermissionManager

### 权限校验：允许操作

**GIVEN** 一个 `PermissionManager` 实例，已配置白名单允许 `FILE_READ` 操作  
**WHEN** 调用 `check(toolCall("file_read", "/workspace/readme.md"), securityContext)`  
**THEN** 返回 `PermissionResult(allowed=true, reason="...")`

### 权限校验：拒绝未授权操作

**GIVEN** 一个 `PermissionManager` 实例，未配置任何白名单  
**WHEN** 调用 `check(toolCall("file_write", "/etc/hosts"), securityContext)`  
**THEN** 返回 `PermissionResult(allowed=false, reason="...")`  
**AND** reason 包含拒绝原因

### 默认实现：安全优先（拒绝所有）

**GIVEN** 使用 `DefaultPermissionManager` 的默认配置  
**WHEN** 对任意 `ToolCall` 调用 `check()`  
**THEN** 返回 `PermissionResult(allowed=false)`  
**AND** 明确拒绝的理由为 "No permission rules configured, deny by default"

### 默认实现：白名单匹配

**GIVEN** `DefaultPermissionManager` 配置了 `allow("FILE_READ", "/workspace/**")`  
**WHEN** 调用 `check(toolCall("file_read", "/workspace/data.txt"), ctx)`  
**THEN** 返回 `PermissionResult(allowed=true)`  
**AND** 匹配到白名单规则

### 默认实现：黑名单优先级高于白名单

**GIVEN** `DefaultPermissionManager` 配置了 `allow("FILE_READ", "/workspace/**")` 和 `deny("FILE_READ", "/workspace/secrets/**")`  
**WHEN** 调用 `check(toolCall("file_read", "/workspace/secrets/key.txt"), ctx)`  
**THEN** 返回 `PermissionResult(allowed=false)`  
**AND** 黑名单规则优先匹配

---

## ApprovalGateway

### 低风险操作自动放行

**GIVEN** 一个 `ApprovalGateway` 实例  
**WHEN** 提交 `ApprovalRequest(operation="FILE_READ", risk=LOW, ...)`  
**THEN** 返回 `ApprovalResponse(approved=true, reason="Auto-approved: low risk")`

### 中高风险操作需要审批

**GIVEN** 一个 `ApprovalGateway` 实例  
**WHEN** 提交 `ApprovalRequest(operation="SHELL_EXEC", risk=HIGH, ...)`  
**THEN** 返回 `ApprovalResponse(approved=false)` 直到审批通过  
**OR** 超时后自动返回 `ApprovalResponse(approved=false, reason="Timeout")`

### 默认实现：超时自动拒绝

**GIVEN** `InMemoryApprovalGateway` 配置了超时 30 秒  
**WHEN** 提交审批请求，且 30 秒内无人审批  
**THEN** 返回 `ApprovalResponse(approved=false, reason="Approval timeout")`

---

## AuditLogger

### 记录工具调用

**GIVEN** 一个 `AuditLogger` 实例  
**WHEN** Agent 执行 `toolCall("file_read", "/data.txt")` 成功  
**THEN** `logExecution()` 被调用  
**AND** 事件包含 `agentId`、`operation=FILE_READ`、`target=/data.txt`、`result=SUCCESS`、`timestamp`

### 记录权限拒绝

**GIVEN** 一个 `AuditLogger` 实例  
**WHEN** `PermissionManager.check()` 拒绝某工具调用  
**THEN** `logDecision()` 被调用  
**AND** 事件包含 `operation=FILE_WRITE`、`result=DENIED`、拒绝原因

### 默认实现：SLF4J 分级

**GIVEN** 使用 `Slf4jAuditLogger`  
**WHEN** 记录安全事件（`result=DENIED`）  
**THEN** 日志级别为 WARN  
**AND** 日志格式为结构化 JSON

---

## SecurityAutoConfiguration

### 默认 Bean 装配

**GIVEN** `cloud-ai.security.enabled=true`（默认）  
**WHEN** Spring 容器启动  
**THEN** 自动装配 `DefaultPermissionManager`、`InMemoryApprovalGateway`、`Slf4jAuditLogger`  
**AND** 三个 Bean 均为 `@ConditionalOnMissingBean`，可被外部替换

### 模块禁用

**GIVEN** `cloud-ai.security.enabled=false`  
**WHEN** Spring 容器启动  
**THEN** 不创建任何安全模块 Bean