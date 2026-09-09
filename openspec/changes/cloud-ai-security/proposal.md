# cloud-ai-security — Proposal

## 问题陈述

当前 `cloud-ai` 框架缺少 Agent 工具调用的安全治理层。当 Agent 执行文件读写、Shell 命令、网络请求等操作时，没有统一的权限校验、审批流程和审计记录机制。这导致：

- **安全风险**：Agent 可能被 LLM 诱导执行危险操作（删除文件、执行恶意命令）
- **合规缺失**：无法追溯 "谁、什么时间、做了什么、结果如何"
- **不可控**：高危操作无法二次确认，缺少 Human-in-the-Loop 机制

## 调研结论

### Spring AI 安全模式

Spring AI 的安全方案主要依赖 **Advisor 链** 实现：

| 机制 | 说明 |
|------|------|
| **Advisor 拦截器** | 在工具调用前后插入权限校验、内容过滤、次数限制 |
| **Spring Security 集成** | HTTP 层粗粒度认证 + AI 调用层 Advisor 细粒度授权 |
| **ToolCallAdvisor** | 递归执行工具调用，每次执行前可做 Permission Check |
| **SafeGuardAdvisor** | 内置安全护栏：敏感词过滤、注入检测 |
| **MCP 沙箱隔离** | 工具运行在独立进程，天然隔离 |

**Spring AI 的局限**：Spring AI 的 Advisor 更偏向"请求/响应拦截"，没有独立的 PermissionManager、ApprovalGateway、AuditLogger 抽象。安全能力分散在各个 Advisor 中，缺少明确的治理 SPI。

### DSH 现有架构

DSH 的 `cloud-ai-llm` 已建立了明确的模式：
- **SPI 在 core 中定义**（`ChatModel`、`ModelDiscovery`）
- **实现类在 llm 中**（`AbstractLlmAdapter`、`DeepSeekLlmAdapter`）
- **模型用 record 类型**（`ChatRequest`、`ChatResponse`、`Message`）
- **自动装配在 config 包中**（`LlmAutoConfiguration`）

安全模块应遵循相同的架构模式。

## 变更内容

### ADDED
- **`cloud-ai-security` 模块**：3 个 SPI 接口 + 3 个默认实现 + 模型定义 + 自动装配
- SPI：`PermissionManager`、`ApprovalGateway`、`AuditLogger`
- 模型：`PermissionResult`、`ApprovalRequest`、`ApprovalResponse`、`AuditEvent`、`SecurityContext`、`OperationType`
- 默认实现：`DefaultPermissionManager`、`InMemoryApprovalGateway`、`Slf4jAuditLogger`
- 装配：`SecurityAutoConfiguration`

### 不变的部分
- `cloud-ai-core` 不依赖安全模块
- `cloud-ai-llm` 不修改
- 现有 ChatClient / ModelRouter 接口不变

## 关键风险

| 风险 | 缓解措施 |
|------|----------|
| 权限检查粒度太粗，无法满足复杂场景 | SPI 设计为 `PermissionManager.check(ToolCall, SecurityContext)`，允许外部按需扩展 |
| 审批超时阻塞 Agent 循环 | `InMemoryApprovalGateway` 提供超时自动拒绝，默认 30s |
| 审计日志量过大 | `Slf4jAuditLogger` 使用 SLF4J 分级，安全事件 WARN，普通操作 DEBUG |

## 依赖

- 仅依赖 `cloud-ai-core`（已完成的 `ToolCall`、`ToolDefinition` 模型）
- 不依赖 `cloud-ai-llm`（安全模块是更底层的底座）