# cloud-ai-p0-hygiene — Proposal

## 问题陈述

对标审查（spring-ai-alibaba / deepseek-harness，见 `docs/gap-analysis-and-roadmap.md`）发现一批
"已宣称存在但实际不可用"的断线与半成品代码。它们不是新能力缺口，而是 P0 卫生问题：

- **Anthropic 适配器半成品**：请求不映射 tools、system 消息未提为顶层字段、响应不解析
  `tool_use` 内容块 —— 函数调用完全不可用；且 `LlmAutoConfiguration` 工厂没有任何分支会创建它
- **`CloudAiApplication.main` 空壳**：路由注册被注释、装配未实现，启动的是无 handler 的空
  HttpServer；读取 `application.properties` 但资源只有 `application.yml`
- **Spring 路径全面断线**：`AgentService` 装配时 skillRegistry 传 `null`；`load_skill` 未注册；
  `ServerAutoConfiguration` 构造器自引用 `ApiKeyInterceptor` 造成循环依赖；
  `ObservationRegistry` 强依赖（无 actuator 的应用无法启动）；`CloudAiProperties` 中带 no-arg
  构造器的嵌套 record（Execution/Memory/Context/Skill/Runtime）因多构造器无法构造绑定，
  **对应配置从未生效**；skills 组件名 `skill`（绑定 `cloud-ai.skill.*`）与开关键
  `cloud-ai.skills.enabled` 命名不一致；Boot 4.1 需要 logback ≥1.5.14 而父 POM 锁定 1.5.12，
  Spring 上下文在日志初始化阶段即崩溃
- **ContextManager 从未接线**：上下文压缩能力存在但 Agent 循环不调用，长对话必然超上下文
- **悬空配置**：`fileDeleteEnabled`（无对应工具）、`execution.sandbox.*`（无绑定）；
  `shell.timeout` 硬编码 30s 不读配置；且 ShellToolExecutor 先同步读尽输出再 waitFor，
  持续输出的长命令**超时永远不会触发**（真实超时 bug）
- **README/openspec 漂移**：宣称 Spring Boot 构建、模块表缺 3 个模块、HTTP API 段落重复

## 变更内容

### MODIFIED

- **P0-1 Anthropic 适配器补全**（`cloud-ai-llm`）：
  - 请求：system → 顶层 `system` 字段；tools → `tools[].input_schema`；assistant ToolCall →
    `tool_use` 内容块（input 为 JSON 对象）；tool 结果消息 → user 角色 `tool_result` 内容块；
    temperature/top_p/stop_sequences 透传；空工具列表不发送 tools 字段
  - 响应：`tool_use` 内容块 → `ToolCall`（input 序列化为 JSON 字符串）；stop_reason 映射补
    stop_sequence
  - 工厂：`createAdapter` 新增 anthropic 分支（按 provider 名或 capabilities）；
    方法改为 package-private 支持测试
  - 流式限制文档化：tool_use 增量聚合（input_json_delta）留待 P1 流式改造，Javadoc 已标注

- **P0-2 `CloudAiApplication` 重写**（`cloud-ai-server`）：
  - 完整装配链（LLM→记忆+ContextManager→人格→安全→工具→技能→运行时→AgentService），
    支持 `modelOverride` 注入（测试桩）
  - 配置文件改为 `cloud-ai-server.properties`（classpath + 外部覆盖 + `${ENV:default}` 占位符
    解析 + 时长解析 60s/10m/ISO）；**刻意避开 `application.*` 命名**，防止 cloud-ai-spring
    场景下被 Spring Boot 误加载（曾实际引发测试注入幽灵 deepseek provider）
  - 删除 `application.yml`；JDK HttpServer 路由注册 + 虚拟线程 + 优雅停机 + 主线程阻塞保活

- **P0-3 Spring 路径修复**（`cloud-ai-spring`）：
  - `SkillsAutoConfiguration` 新增 `loadSkillExecutor` Bean 并注册进 ToolRegistry（无技能时不注册）
  - `ServerAutoConfiguration`：AgentService 注入 `ObjectProvider<SkillRegistry>`；移除
    `ApiKeyInterceptor` 构造器自引用（循环依赖），改为依赖 `CloudAiProperties`
  - `LlmAutoConfiguration`：`ObservationRegistry` 改 `ObjectProvider` 可选注入（无 actuator 回退 NOOP）
  - `CloudAiProperties`：组件 `skill` 改名 `skills`（对齐 `cloud-ai.skills.*` 命名空间）；
    多构造器 record 的规范构造器标注 `@ConstructorBinding`，原始类型组件加 `@DefaultValue`
  - `Execution` 组件对齐新 ExecutionProperties（shellTimeout/workspace，移除 fileDeleteEnabled）
  - `Security.Approval` 增加 mode；`RuntimeAutoConfiguration` 接线 ContextManager + maxContextTokens
  - POM：logback 对齐 Boot 4.1 管理的 1.5.34（模块内覆盖父 POM 的 1.5.12）
  - 新增上下文测试（模块此前零测试）：默认装配 / 有技能注册 load_skill / skills 禁用

- **P0-4 ContextManager 接入 AgentLoop**（`core` + `memory` + `runtime`）：
  - `ContextManager` 接口从 `com.cloudai.memory` 迁移到 `com.cloudai.core.chat`
    （共享词汇表归 core；runtime 消费、memory 实现的跨模块契约）
  - `AgentLoopBuilder` 新增 `contextManager(...)` / `maxContextTokens(...)`（默认 null 不裁剪，行为
    完全兼容）；`ReActAgentLoop`/`PlanThenExecuteAgentLoop` 新构造器；`callModel` 前压缩**请求视图**
    （会话原始历史不受影响）

- **P0-5 悬空配置清理**（`execution` + `security`）：
  - `ExecutionProperties`：移除 `fileDeleteEnabled`，新增 `shellTimeout`、`workspace`；
    工厂签名改为 `toolRegistry(ExecutionProperties)`；`FileWriteExecutor` 补 baseDir 构造器
  - `ShellToolExecutor`：超时可配置 + **修复超时失效 bug**（输出读取移至后台线程，主线程
    waitFor 超时判定后 destroyForcibly）
  - 新增 `ApprovalMode`（AUTO/MANUAL）：`InMemoryApprovalGateway` 支持模式；
    `SecurityProperties.Approval.mode` 绑定（非法值 fail fast）
  - 删除 `application.yml` 中无绑定的 `sandbox.*`、`management.*`、`spring.*` 键

- **P0-6 文档对齐**：README 重写（12 模块、纯 JDK 定位、双轨配置说明、去重）；
  `openspec/project.md` 更新为现行领域分包约定

### 不变的部分

- `AgentLoop.run()` 同步骨架与三级生命周期 SPI 签名不变
- 所有既有构造器保持兼容（新增重载而非改签名，旧调用点零改动）
- 未配置 ContextManager / 未改动配置时，循环与工具行为与变更前一致

## 关键风险

| 风险 | 缓解措施 |
|------|----------|
| ContextManager 迁移 core 属于破坏性移动 | 项目为 1.0-SNAPSHOT 内部框架；全仓引用点已同步（memory/spring/tests），编译期即可发现遗漏 |
| Shell 超时修复改变进程交互时序 | 输出缓冲同步化（synchronized append），读线程 daemon + join 上限；新增超时行为测试 |
| logback 模块内升版影响其他模块 | 仅在 cloud-ai-spring 的 dependencyManagement 覆盖（子模块优先于父），其余模块维持 1.5.12 |
| spring 组件改名 `skills` | 与 `@ConditionalOnProperty("cloud-ai.skills.enabled")` 对齐，属修 bug 而非行为变更 |

## 验证

`mvn test`（12 模块全量）；新增/变更测试：
- `AnthropicAdapterTest`（重写为协议映射单测 8 例）、`LlmAutoConfigurationTest`（7 例）
- `ContextCompressionTest`（4 例）、`ExecutionAutoConfigurationTest`（4 例）、
  `ShellToolExecutorTest.Timeout`、`FileWriteExecutorTest.WorkspaceResolution`（2 例）
- `InMemoryApprovalGatewayTest.ApprovalModeBehavior`（2 例）、`SecurityAutoConfigurationApprovalModeTest`（3 例）
- `CloudAiApplicationTest`（12 例，含端到端 HTTP + Stub 模型）
- `cloud-ai-spring` 上下文测试（4 例，模块首批测试）
