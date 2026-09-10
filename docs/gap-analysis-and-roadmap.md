# cloud-ai 对标审查与演进规划

> 对标对象：[Spring AI Alibaba](https://java2ai.com/docs/overview/)（下称 SAA）、[DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)（下称 dsh）
> 审查日期：2026-09-10 ｜ 审查范围：本仓库全部 12 个模块（约 204 个 Java 文件、340 个测试）
> 结论效力：本文是规划文档，不改变任何现有代码；每项落地前应按 `openspec/changes/` 流程立独立变更提案（参考 `cloud-ai-security` 先例）

---

## 1. 执行摘要

cloud-ai 已具备一个 Agent Harness 的**骨架完整度**：12 模块分层清晰、ReAct / PlanThenExecute 双循环、三级生命周期 SPI、权限-审批-审计安全链、注解式工具注册 + JSON Schema 推导、技能渐进加载，测试覆盖扎实。但与两个对标项目相比，存在四类核心差距：

1. **不可观测、不可回放**：Agent 循环没有事件流与会话日志（dsh 以 session log 为"模型所见上下文的唯一事实源"），导致流式输出、回放、fork、审计增强都无从谈起。
2. **无流式端到端**：LLM 层流式能力完整（`stream()`/`MessageAggregator`/SSE 解析都在），但 `AgentLoop` 只有阻塞 `run()`，HTTP 层无 SSE —— 能力已付费未通电。
3. **无编排层**：SAA Graph（条件路由/并行/子图/checkpoint/多 Agent 模式）在 cloud-ai 中完全缺失，只有两个硬编码循环模式。
4. **上下文工程未闭环**：`ContextManager` 压缩从未接入循环（长对话必然超上下文）、无 token 预算、无结构化输出、无 MCP。

同时存在一批**已实现但断线/半成品的代码**（P0）：Anthropic 适配器不传工具且工厂不会创建它、`CloudAiApplication.main` 是空壳服务器、Spring 路径技能装配传 `null`、多处悬空配置。

**优先级总览**：

| 阶段 | 主题 | 核心交付 | 预估 |
|------|------|----------|------|
| P0 | 卫生修复 | 修复 6 处断线/半成品，不加新能力 | 1~2 周 |
| P1 | 运行时核心 | 事件流 + 流式端到端、上下文压缩接线、结构化输出、todo 工具、并行步骤、Agent 级观测 | 4~6 周 |
| P2 | 编排与生态 | cloud-ai-graph 模块、多 Agent 模式、HITL/checkpoint、MCP 客户端、Embedding/向量记忆、会话持久化 | 6~10 周 |
| P3 | 执行世界与规模化 | 沙箱/ExecutionWorld、web 工具、凭证管理、hooks/SDK、Studio（可选） | 按需 |

---

## 2. 对标项目能力画像

### 2.1 Spring AI Alibaba（1.1.2.x）

三层架构：**Augmented LLM**（基于 Spring AI 原子抽象）→ **Graph**（工作流与多 Agent 运行时基座）→ **Agent Framework**（ReactAgent 开发框架）。

| 能力域 | 具体能力 |
|--------|----------|
| Graph 编排 | `StateGraph`/`Node`/`Edge`/`OverAllState` 四构件；条件边（Dispatcher）、并行分支、嵌套子图；预置节点；PlantUML/Mermaid 可视化导出；Dify DSL 转换 |
| 多 Agent | 内置模式：`SequentialAgent`（顺序）、`ParallelAgent`（并行）、`LoopAgent`（循环）、`LlmRoutingAgent`（路由）、Supervisor（监督者）；预置应用 DeepResearch、DataAgent、JManus |
| 人机协同 | Human-in-the-loop：中断等待人工确认/修改状态后恢复 |
| Agent Framework | ReactAgent 十行代码建应用；自动上下文工程；流式；错误恢复与重试 |
| 基础层 | 多模型（DashScope/OpenAI/类 OpenAI）、多模态（文生图/语音）、Structured Output（POJO 映射）、Vector Store、Function Calling、MCP、RAG 离线管道（Reader/Splitter/Embedding/VectorStore/Retrieve） |
| 生态 | Spring AI Alibaba Studio（Web UI 调试）、Project Initializer、Nacos 支持 A2A 分布式智能体通信 |

### 2.2 DeepSeek Harness（dsh，TypeScript，开发者预览）

定位与 cloud-ai 最接近：**编码 Agent Harness**。核心理念"一切皆插件"，构建在 Cordis 插件运行时上（Service Definition / Provider / Consumer 三角色"接缝"）。

对 cloud-ai 最有参考价值的机制（按包分组）：

| 机制 | 设计要点 |
|------|----------|
| **会话日志即事实源** | 模型可见当且仅当已入日志；`deriveMessages()` 从日志投影模型历史；fork/resume/transcript/遥测全部从日志派生；运行时有断言校验该不变式 |
| **turn/step 循环** | step = 一次模型请求 + 其工具调用；turn = 零或多步；事件化生命周期（`agent/pre-step`、`llm/stream`、`tools/pre-execute` 等 waterfall/serial 事件） |
| **上下文工程** | `context` 包 + `compaction` 压缩；`spill` 超限外溢；inbox 机制区分"立即唤醒"与"排队注入"的输入 |
| **编排工具** | subagent 委派、workflow（worker-thread）、todo_write、plan mode、goal、schedule/jobs 后台工作 |
| **人类交互** | ask-user、approval、permission-presets、feedback |
| **执行世界** | shell/fs/subprocess/LSP 共享同一执行世界（seam），provider 指向远程沙箱（e2b）时整套工具无差别迁移 |
| **数据平面** | session 持久化/投影/查询、workspace、settings、credentials |
| **对外接口** | Web GUI（host/client）、SDK、ACP、hooks、headless 单次运行器 |

### 2.3 采纳决策

**采纳**：会话事件日志、上下文压缩与 token 预算、流式端到端、结构化输出、MCP、todo/ask-user 内置工具、并行工具执行、图编排与多 Agent 模式、checkpoint + HITL、沙箱化的执行世界抽象、凭证管理。

**不采纳（范围纪律）**：

| 不采纳项 | 理由 |
|----------|------|
| Cordis 式统一插件运行时 | cloud-ai 的"领域 SPI 接口 + 静态工厂 config + Spring 条件装配"已等价覆盖扩展需求；引入插件容器收益低、复杂度高 |
| Studio Web UI（前期） | 先补 README/示例/trace 导出；UI 在核心稳定后再评估 |
| A2A / Nacos 分布式 | 单机 Harness 场景外 |
| 多模态（文生图/语音） | 无用例驱动，仅保留 `ChatModel` 之外的扩展位，不预先实现 |
| Dify DSL 导入、低代码对齐 | 非 targeting 场景 |

---

## 3. cloud-ai 现状盘点（摘要）

### 3.1 已有强项（对标中不落后甚至更细的部分）

- **安全治理**：`SecurityInterceptor` 五阶段编排（访问→权限→风险→审批→决策），黑白名单 + Ant 路径匹配 + 同步审批网关 + 结构化审计，11 个测试类 66 测试 —— 比 SAA 分散在 Advisor 里的方案更成体系。
- **工具注册**：`@Tool`/`@ToolParam` 注解扫描 + Java 类型自动推导 JSON Schema（含枚举/集合/约束全集）+ 反射执行，比 dsh 手写 schema 体验好。
- **技能渐进加载**：`SkillRegistry` 只注册摘要 + `load_skill` 工具按需载入全文，与 Claude Code/dsh 同款模式。
- **提示词组装**：`PromptSection` order 约定（10~69）+ 五段默认拼装（Persona/Environment/ProjectContext/SkillMenu/Memories）。
- **三级生命周期 SPI**：Session/Turn/Step 三层策略注入，骨架 `final`，是补流式/并行的天然切口。
- **纯 JDK + 可选 Spring 双轨**：框架模块零 Spring，`cloud-ai-spring` 只做装配壳 —— 与 SAA 强绑 Spring 形成差异化。

### 3.2 主要弱项

模型层（无结构化输出/Embedding）、运行时（无事件/流式/并行/预算）、编排（无图/多 Agent）、持久化（全内存）、生态（无 MCP/web 工具）、传输（无 SSE）。详见第 4 节矩阵。

### 3.3 已发现的断线/半成品（P0 素材）

| # | 问题 | 位置 |
|---|------|------|
| 1 | Anthropic 适配器不映射 tools、system 未提为顶层字段、不解析 `tool_use`；且 `LlmAutoConfiguration` 工厂没有任何分支会创建它 | `cloud-ai-llm` |
| 2 | `CloudAiApplication.main` 的路由注册被注释、装配未实现，启动的是无 handler 的空 HttpServer；读 `.properties` 但资源只有 `application.yml` | `cloud-ai-server` |
| 3 | Spring 路径 `ServerAutoConfiguration` 创建 `AgentService` 时 skillRegistry 传 `null`；`SkillsAutoConfiguration` 未注册 `load_skill` 工具 | `cloud-ai-spring` |
| 4 | `ContextManager`（上下文压缩）只在工厂/Spring Bean 存在，AgentLoop 从不调用 —— 长对话必然超上下文 | `cloud-ai-memory`/`runtime` |
| 5 | 悬空配置：`fileDeleteEnabled`（无对应工具）、`shell.timeout`/`sandbox.*`/`filesystem.workspace`/`approval.mode` 无绑定类 | `execution`/`server` |
| 6 | README 与现状漂移：宣称"基于 Spring Boot 4.1 构建"（现为纯 JDK + 可选 Spring）、HTTP API 段落重复两遍、模块表缺 3 个模块 | `README.md` |

---

## 4. 差距分析矩阵

图例：✅ 完整 ｜ ⚠️ 部分/未接线 ｜ ❌ 缺失

| 能力域 | cloud-ai | SAA | dsh | 差距结论 |
|--------|----------|-----|-----|----------|
| LLM 适配（多厂商/重试/流式） | ✅ | ✅ | ✅ | 持平；Anthropic 适配器需修复 |
| 结构化输出（JSON Schema/POJO） | ❌ | ✅ | ✅（结构化工具参数） | **补** |
| Embedding / 向量检索记忆 | ❌（仅关键词检索） | ✅ | —（外挂） | **补**（P2） |
| Token 计量与预算 | ⚠️（被动累计 usage） | ⚠️ | ✅ token-meter | **补预算执行** |
| 上下文压缩 compaction | ⚠️（有实现未接线） | ✅ 自动上下文管理 | ✅ + spill | **先接线再增强** |
| Agent 循环（ReAct 等） | ✅ | ✅ ReactAgent | ✅ | 持平 |
| 图/工作流编排 | ❌ | ✅ Graph | ✅ workflow | **补**（P2，最大单项差距） |
| 多 Agent 模式（顺序/并行/路由/监督者） | ❌ | ✅ 5 种 | ⚠️ subagent 委派 | **补**（P2） |
| 并行工具调用 | ❌（默认串行，留了覆盖点） | ✅ | ✅ | **补**（P1，切口已留好） |
| 流式端到端（循环→HTTP） | ⚠️（LLM 层有，循环/传输无） | ✅ | ✅ | **补**（P1 核心） |
| 事件流 / 会话日志 / 回放 fork | ❌ | ⚠️（checkpoint/快照） | ✅（核心不变式） | **补**（P1 核心，借鉴 dsh） |
| checkpoint 持久化 + 恢复 | ⚠️（仅 history 重放） | ✅ | ✅ session 持久化 | **补**（P2） |
| Human-in-the-loop | ⚠️（同步审批 30s） | ✅（图节点级中断恢复） | ✅（ask-user/approval/feedback） | **增强**：异步审批 + ask_user |
| 内置任务管理工具（todo/plan） | ❌ | — | ✅ todo/plan/goal | **补**（P1，低成本） |
| MCP 客户端 | ❌ | ✅ | ✅ | **补**（P2 头部项） |
| Web 工具（search/fetch） | ❌ | ✅（生态） | ✅ | 补（P3，HttpTool 简单） |
| LSP / 代码智能 | ❌ | — | ✅ | 可选（P3） |
| 沙箱 / 执行世界切换 | ❌（本地直跑） | ⚠️（MCP 进程隔离） | ✅（本地/e2b，seam 整体迁移） | **补抽象**（P3） |
| 权限模型 | ✅ 黑白名单 | ⚠️ Advisor | ✅ + permission-presets | 补 presets（P2 小项） |
| 审计 | ✅ 三阶段结构化 | — | ✅ 事件日志派生 | 与事件流合并增强 |
| 观测 | ⚠️（仅 LLM 层 Observation） | ✅ | ✅ 遥测 | **补 Agent 层 span**（P1） |
| 凭证管理 | ⚠️（明文配置/env） | — | ✅ credentials 包 | 补（P3） |
| 传输（HTTP API） | ✅ 同步 JSON | ✅ | ✅ Web GUI/ACP/SDK | 补 SSE（P1）；WS/ACP（P3） |
| 持久化存储 | ❌ 全内存 | ✅ | ✅ | 补 SPI + 文件实现（P2） |
| 开发体验（Builder/示例/文档） | ✅ Builder+Stub 示例 | ✅ Studio/Initializer | ✅ | 补 README 修正 + spring 模块测试 |

---

## 5. P0：既有能力修复（不加新能力）

> 目标：把"已宣称存在"的能力变成真实可用。全部为小改动，每个项独立可交付、可单独提交。

### P0-1 Anthropic 适配器补全或显式降级

- **现状**：`AnthropicLlmAdapter.buildRequestBodyInternal` 丢弃 tools、system 消息未转 Anthropic 顶层 `system` 字段、响应不解析 `tool_use` 内容块；`LlmAutoConfiguration` 无创建它的分支。
- **方案**：
  1. 补全请求映射（tools → `tools[]`、system → 顶层 `system`、tool 结果消息 → `tool_result` 内容块）与响应解析（`tool_use` block → `ToolCall`）；
  2. 工厂按 provider 能力选择适配器（新增 `anthropic` 分支，capabilities 含 `anthropic-messages` 或按 base-url 判定）。
- **验收**：`AnthropicAdapterTest` 覆盖纯文本、带工具请求、tool_use 响应解析、流式 content_block_delta 四类；工厂测试覆盖三分支选择。
- **工作量**：M。**替代方案**：若短期不维护，先在 Javadoc 与 README 标注"仅纯文本，函数调用不可用"，并从宣传口径移除 —— 二选一，不允许维持现状。

### P0-2 `CloudAiApplication.main` 修复

- **现状**：`server.createContext` 注册行被注释，启动的是空 HttpServer；读 `application.properties` 但只提供 `application.yml`。
- **方案**：main 改读 `application.yml`（或补一份 `.properties` 样例并统一）；按 `application.yml` 装配 `CloudAi.builder()`（复用 facade）后注册 `AgentHttpHandler` 三路由；无 LLM 配置时给出明确启动失败信息（而不是空转）。
- **验收**：集成测试（JDK HttpServer 启动于随机端口 + StubChatModel）走通 `POST /api/agent/run`。
- **工作量**：S~M。

### P0-3 Spring 路径技能断线

- **现状**：`ServerAutoConfiguration` 给 `AgentService` 传 `null` skillRegistry；`SkillsAutoConfiguration` 不注册 `load_skill`。
- **方案**：注入真实 `SkillRegistry`；在 `ExecutionAutoConfiguration`（或 Skills 子配置）中把 `LoadSkillExecutor` 注册进 `ToolRegistry`（对齐 `CloudAi` facade 的做法）。
- **验收**：`cloud-ai-spring` 增加上下文测试（该模块允许 `@SpringBootTest`）：Bean 装配非空、工具列表包含 `load_skill`。
- **工作量**：S。

### P0-4 ContextManager 接入 Agent 循环

- **现状**：压缩能力存在但 `AgentLoopTemplate.executeTurn` 从不调用，历史无限增长。
- **方案**：`AgentLoopBuilder` 增加可选 `ContextManager`（默认 null 不改变行为）；`callModel` 前对 `session.history()` 做 `compress(messages, maxContextTokens)`，用返回列表构建 `ChatRequest`（session 原始 history 保留，仅裁剪请求视图）。
- **验收**：ReAct 循环测试：超预算历史被截断、system 消息保留、不配置时行为与现状完全一致（BCDE 的 Border：空历史/恰好达标）。
- **工作量**：M。此项是 P1 上下文工程的前置。

### P0-5 悬空配置清理

- **现状**：`ExecutionProperties.fileDeleteEnabled` 无对应工具；`application.yml` 的 `execution.shell.timeout`、`execution.sandbox.*`、`execution.filesystem.workspace`、`security.approval.mode` 无绑定。
- **方案**：原则"配置项必须可追溯到一个消费方"。`shell.timeout` → `ExecutionProperties.shellTimeout` 并让 `ShellToolExecutor` 读取（替换 30s 硬编码）；`filesystem.workspace` → 作为 FileRead/FileWrite 的 baseDir 默认值；`approval.mode` → 映射 `InMemoryApprovalGateway` 预设（auto=LOW 放行 / manual=全部审批）；`sandbox.*`、`fileDeleteEnabled` → 删除，待 P3 沙箱立项时重新设计；README 配置样例同步。
- **验收**：每个保留配置项有绑定测试；`mvn test` 全绿。
- **工作量**：M。

### P0-6 README 与 openspec/project.md 对齐现状

- **现状**：README 宣称 Spring Boot 构建、模块表缺 `context`/`skills`/`spring`、HTTP API 段重复；`openspec/project.md` 仍是 spi/impl/model 四层约定（已被领域分包取代）。
- **方案**：按 AGENTS.md 现行约定重写架构图与模块表；去重；补 cloud-ai-spring 用法一节。纯文档，无代码风险。
- **工作量**：S。

---

## 6. P1：运行时核心补齐

> 主线：**事件化 + 流式 + 上下文闭环**。P1 完成后，cloud-ai 从"能跑"变成"可运维"。

### P1-1 会话事件流与 SessionLog（借鉴 dsh，本阶段地基）

- **差距**：dsh 的核心不变式"模型可见当且仅当已入日志"；cloud-ai 的 `AgentSession` 是纯内存可变状态，外部无法观察循环内部。
- **方案**（`cloud-ai-runtime` 内新增领域包）：
  - `runtime.event.AgentEvent`：sealed 接口 + record 族 —— `SessionStarted` / `TurnStarted` / `ModelResponded` / `ToolCallRequested` / `ToolCompleted` / `TurnCompleted` / `SessionCompleted` / `SessionFailed` / `SessionInterrupted`（均携带 traceId/turn 序号/usage 增量）；
  - `runtime.session.SessionLog`：append-only 事件日志，`deriveMessages()` 投影出模型历史（对齐 dsh 语义：进入模型请求的内容必须可从日志重建）；
  - `AgentLoopTemplate` 骨架在既有生命周期点位发布事件（不改变现有 SPI 签名）；
  - `runtime.session.SessionStore` SPI（先 `InMemorySessionStore`，P2 加文件实现），支撑跨进程恢复。
- **验收**：单次 ReAct 运行的事件序列快照测试；`deriveMessages()` 与 session history 等价性测试；事件不可变性测试。
- **工作量**：L（是 P1 其余项的依赖）。

### P1-2 流式端到端

- **差距**：LLM 层 `Flux<ChatResponse>` 全套存在但循环不用、传输层无 SSE。
- **方案**：
  - `AgentLoop` 增加 `default Flux<AgentEvent> runStream(AgentRequest)`（默认实现 `Flux.just(...)` 包装同步 `run()` 的首尾事件 —— 伪流式保底）；
  - `ReActAgentLoop` 真实现：`TurnLifecycle.callModel` 改造为可同时走 `stream()` 并把 chunk 聚合（复用 `MessageAggregator`），chunk 透传为 `ModelChunked` 事件；
  - `cloud-ai-server`：`POST /api/agent/stream` 返回 SSE（JDK HttpServer 手写 SSE 响应头 + 逐事件刷写）；`cloud-ai-spring`：同路由返回 `Flux<ServerSentEvent<AgentEvent>>`。
- **验收**：Stub 流式 ChatModel 的端到端测试（server 模块用 JDK HttpServer + 虚拟线程拉取 SSE 断言事件顺序）；流式中断（客户端断连 → interrupt 传播）测试。
- **工作量**：L。
- **注意**：现有 `AbstractLlmAdapter` 流式为阻塞读入 `Flux.create`，顺手补：取消传播（cancel → 关闭 HTTP 连接）与 `doFinally` 清理，对齐 AGENTS.md 流式规范。

### P1-3 上下文工程闭环（compaction + token 预算）

- **差距**：dsh 有 compaction + spill + token-meter；SAA 有自动上下文管理。P0-4 只是"截断"，此处升级为"压缩"。
- **方案**：
  - `cloud-ai-memory`：新增 `SummarizingContextManager`（`Default` 语义实现）：超预算时用 LLM 把被裁剪的早期对话摘要为一条 system 段 `[Earlier conversation summary]`（离线压缩，不在主循环内联调用）；保留 `SimpleContextManager` 为零 LLM 依赖默认项；
  - `cloud-ai-llm`：`ModelOptions`/配置透传各 provider 的 max context tokens（已有 `maxContextTokens` 配置，接到预算计算）；
  - `runtime`：`AgentSession` 增加累计 token 预算检查（`maxTotalTokens`，超出 → `FinishStatus` 新增 `BUDGET_EXCEEDED` 终止）。
- **验收**：摘要压缩的 mock-LLM 测试（摘要被注入、原文被裁）、预算超限终止测试、与 P0-4 截断行为的边界衔接测试。
- **工作量**：M~L。

### P1-4 结构化输出

- **差距**：SAA 支持 POJO 映射；cloud-ai 完全没有，这是做数据抽取/表单填充类 Agent 的硬门槛。
- **方案**：
  - `core.chat`：`ChatRequest`/`ModelOptions` 增加 `responseSchema`（JSON Schema 字符串，保持 core 零依赖）；
  - `cloud-ai-llm`：`AbstractLlmAdapter` 映射 OpenAI `response_format: {type: json_schema}`（DeepSeek 同协议）；
  - `llm.client`：`CallResponseSpec.entity(Class<T>)` —— Jackson 反序列化 + 校验失败重试一次（带错误信息要求模型自纠）。
- **验收**：请求体包含 response_format 的构造测试；entity 反序列化成功/失败/自纠重试三类测试。
- **工作量**：M。

### P1-5 内置任务管理工具 todo

- **差距**：dsh 用 todo_write 让长任务可规划可追踪；cloud-ai 多轮循环没有任务状态锚点。
- **方案**：`execution.builtin.TodoWriteExecutor`（`todo_write` 工具：全量替换式 todo 列表，session 隔离存储于 `AgentSession.metadata` 或独立 `TodoStore`）；`AgentService`/facade 把当前 todo 摘要注入下一轮 prompt 段（order=55）。
- **验收**：写入/替换/读取回显测试；循环中 todo 状态跨轮保持测试。
- **工作量**：S~M。

### P1-6 并行工具执行

- **差距**：默认 `executeSteps` 串行；SAA/dsh 均支持并行。
- **方案**：`runtime.loop.ParallelStepLifecycle`（装饰 `StepLifecycle`，虚拟线程 `ExecutorService` 并行 `doStep`，按到达顺序回写 tool 消息 —— 注意：并行回写顺序不影响语义，因 tool 消息以 toolCallId 关联）；`AgentLoopBuilder.parallelSteps(int)` 开启，默认仍串行（行为兼容）。安全约束：并行执行同样各自过 `SecurityInterceptor`，无共享可变状态。
- **验收**：两个慢工具（latch 控制）并行总时长 < 串行；单工具失败不影响其他工具结果回写；并行度上限测试。
- **工作量**：M。

### P1-7 Agent 级观测 + trace 导出

- **差距**：Observation 仅覆盖 LLM 调用；循环/工具无 span；无可视化。
- **方案**：
  - `runtime.observation`：`AgentTurnObservationConvention` / `ToolExecObservationConvention`，在 `executeTurn`/`doStep` 埋点（key：agent.type、turn.index、tool.name、result.success）；
  - `runtime.trace.MermaidTraceRenderer`：从 `SessionLog` 渲染 Mermaid 时序图，`AgentResponse.metadata` 携带（低成本对齐 SAA 的图可视化卖点）。
- **验收**： ObservationRegistry 收集器断言 span 层级（session→turn→llm/tool）；Mermaid 输出包含全部工具调用节点。
- **工作量**：M。

### P1-8 cloud-ai-spring 测试补齐

- **差距**：模块零测试，而它是所有 Spring 用户的入口。
- **方案**：`@SpringBootTest` 上下文装配测试（属性开关、`@ConditionalOnMissingBean` 替换、API Key 拦截器、异常映射），Mock `ChatModel` Bean。
- **工作量**：M。

---

## 7. P2：编排与生态（Graph / 多 Agent / MCP / 向量记忆）

> 主线：从"单 Agent 循环"到"可编排的多智能体系统"，对齐 SAA Graph 的能力子集 + dsh 的 subagent/沙箱预设。

### P2-1 新模块 `cloud-ai-graph`

- **差距**：SAA Graph 是其区别于 Spring AI 的核心模块；cloud-ai 编排能力为零。
- **方案**：新增模块（父 POM `<modules>` + `<dependencyManagement>` 注册），依赖 `core` + `llm` + `execution`（复用 `AgentLoop` 与工具体系）。领域分包：
  - `graph`：`StateGraph`（addNode/addEdge/addConditionalEdges/compile）、`GraphNode`（`node_async` 风格异步节点）、`GraphEdge`、`OverAllState`（keyStrategyFactory + Serializer 钩子）；
  - `graph.agent`：`AgentNode`（包装一个 `AgentLoop` 为节点）、`ToolNode`、`HumanFeedbackNode`；
  - `graph.pattern`：`SequentialGraph` / `ParallelGraph` / `LoopGraph` / `LlmRoutingGraph` / `SupervisorGraph` 工厂（对齐 SAA 五模式，实为预组装的图模板）；
  - `graph.checkpoint`：`CheckpointStore` SPI + `InMemoryCheckpointStore` + `FileCheckpointStore`（JSON 序列化 `OverAllState`）；
  - `graph.config.GraphAutoConfiguration` / `GraphProperties`（静态工厂）。
- **取舍**：不实现子图嵌套的完整递归语义（第一版子图编译为独立 `StateGraph` 由节点调用）；不做 Dify DSL 导入。
- **验收**：条件路由/并行汇聚/checkpoint 恢复/HITL 中断恢复四组核心测试（纯 JUnit + Mock LLM 节点）。
- **工作量**：XL（拆 2~3 个 openspec 变更分批交付：核心图 → checkpoint/HITL → pattern 库）。

### P2-2 Human-in-the-loop 增强（异步审批 + ask_user）

- **差距**：现状审批同步阻塞最长 30s，无 agent 主动向用户提问的能力（dsh：ask-user/feedback；SAA：HumanFeedbackNode）。
- **方案**：
  - `security.approval.ApprovalGateway` 增加 `default CompletableFuture<ApprovalResponse> requestApprovalAsync(...)`（同步方法包装保底）；`InMemoryApprovalGateway` 原生异步实现，配合 P1-1 事件流发布 `ApprovalRequested` 事件，server 暴露 `GET /api/agent/approvals/pending` + `POST /api/agent/approvals/{id}/decision`；
  - `execution.interaction.AskUserGateway` SPI（execution 域内新子域）+ `execution.builtin.AskUserExecutor`（`ask_user` 工具：问题 + 选项 → 挂起等待答案，超时策略同审批）；server 提供问答回传端点，答案作为 tool 结果回写循环。
- **验收**：异步审批非阻塞测试（主线程不等待即返回事件）；ask_user 全链路（提问→外部回答→循环继续）测试；超时拒绝测试。
- **工作量**：L。

### P2-3 MCP 客户端（新模块 `cloud-ai-mcp`）

- **差距**：两大对标均支持；MCP 是工具生态的最低成本入口。
- **方案**：新增模块，依赖 `core` + `execution`：
  - `mcp.protocol`：JSON-RPC 2.0 DTO（initialize/tools-list/tools-call/callstones 最小集）；
  - `mcp.client`：`McpClient` SPI + `StdioMcpClient`（子进程 stdin/stdout，JDK `ProcessBuilder`）+ `HttpMcpClient`（JDK HttpClient，Streamable HTTP）；握手、能力协商、超时；
  - `mcp.bridge.McpToolProvider`：把远端工具映射为 `ToolDefinition` + `ToolExecutor` 并注册进 `ToolRegistry`（走 `SecurityInterceptor`，`OperationType` 映射 `CUSTOM` 起步）；
  - `mcp.config.McpAutoConfiguration` / `McpProperties`（servers 列表：type/command/url/apiKey/timeout）。
- **验收**：协议编解码单测；用内存管道模拟 server 的握手/列表/调用集成测试（不真实起外部进程发包的边界用例除外）；工具桥接后安全链路仍生效测试。
- **工作量**：L~XL。

### P2-4 Embedding 与向量记忆

- **差距**：检索记忆仅关键词匹配；SAA 有完整 Vector Store/RAG 管道。
- **方案**（按需最小集，不做通用 RAG 平台）：
  - `cloud-ai-llm` 新增 `llm.embedding` 包：`EmbeddingModel` SPI + `OpenAiEmbeddingAdapter`（`/embeddings` 端点，复用 `AbstractLlmAdapter` 的 HTTP/重试/观测基建）；
  - `cloud-ai-memory` 新增 `memory.vector` 包：`VectorStore` SPI + `InMemoryVectorStore`（余弦相似度）+ `VectorMemoryRetriever`（替换默认 `KeywordMemoryRetriever` 的可选项）；
  - 记忆写入路径：facade/AgentService 持久化交互记忆时同步写向量（带开关）。
- **验收**：embedding 请求构造/响应解析测试；向量检索排序测试；关键词/向量检索可切换测试。
- **工作量**：M~L。外部向量库（pgvector/Milvus）留 SPI 扩展位，不在本阶段实现。

### P2-5 会话持久化与恢复

- **差距**：重启全丢；dsh session 持久化/投影/查询完整。
- **方案**：`runtime.session`：`FileSessionStore`（JSONL 逐事件落盘，按 traceId 分文件）；`AgentService` 支持 `resume(traceId)`：从 `SessionLog.deriveMessages()` 恢复历史继续运行（替代现有裸 history 重放）；facade/HTTP 暴露 list/get。
- **验收**：写入-重启（新实例加载文件）-恢复运行测试；损坏行容错（跳过并告警）测试。
- **工作量**：M。

### P2-6 权限预设（permission-presets）

- **差距**：dsh 有预设档位；cloud-ai 每次要手写规则链。
- **方案**：`security.permission.PermissionPresets`：`readOnly()` / `workspaceWrite()` / `fullAuto()` / `manualApproval()` 静态工厂，产出预配置的 `DefaultPermissionManager` 规则链；配置键 `cloud-ai.security.preset`（P0-5 的 `approval.mode` 并入此处）。
- **验收**：四档预设对各 OperationType 的判定矩阵测试（`@ParameterizedTest`）。
- **工作量**：S。

### P2-7 Skill triggers 消费

- **差距**：`Skill.triggers` 解析后无任何消费方。
- **方案**：`skills` 模块新增 `SkillMatcher`（关键词触发匹配，命中则把该技能提到 SkillMenu 顶部标注"建议使用"或直接预加载全文，配置选择激进/保守模式）。
- **验收**：命中/未命中/多技能竞争排序测试。
- **工作量**：S。

---

## 8. P3：执行世界与规模化（按需立项）

| 项 | 内容 | 借鉴 | 备注 |
|----|------|------|------|
| P3-1 ExecutionWorld / 沙箱 | `execution.world` 领域包：`ExecutionWorld` SPI 统一 fs/subprocess/工作目录/环境变量白名单，`LocalExecutionWorld` 默认实现；远程沙箱（容器/E2B 风格）后续按 provider 接入，工具零改动整体迁移 | dsh seam 设计（shell 是范例） | 先做本地加固（工作目录禁闭、env 白名单、超时），远程为独立变更 |
| P3-2 Web 工具 | `execution.builtin.WebFetchExecutor` / `WebSearchExecutor`（JDK HttpClient、域名白名单走 `NETWORK_CALL` 权限、超时、响应体大小上限） | dsh web 包 | S~M，随时可提前 |
| P3-3 LSP/代码智能 | `execution.lsp`：LSP 客户端（诊断/定义跳转/符号检索），依赖 P3-1 执行世界 | dsh lsp 包 | 仅编码场景需要，按用例立项 |
| P3-4 凭证管理 | `llm.credentials.CredentialProvider` SPI：env / 配置文件 / 系统 keychain；禁止 api-key 进日志与 SessionLog | dsh credentials 包 | 与安全审计联动 |
| P3-5 Hooks/SDK | 运行前/后钩子（对齐 dsh hooks 事件语义，基于 P1-1 事件流实现 waterfall 拦截）；独立 SDK artifact 供嵌入式使用 | dsh hooks/sdk | 事件流稳定后自然衍生 |
| P3-6 Studio / Web UI | 最小 Web 控制台：会话列表 + 事件时间线 + 审批操作 | SAA Studio / dsh Web GUI | 核心稳定后评估，不承诺 |

---

## 9. 模块与依赖演进图（规划态）

```
cloud-ai-core          核心词汇（chat/prompt/tool；P1+responseSchema）
  ↑
cloud-ai-llm           适配层（P1+结构化输出；P2+llm.embedding）
cloud-ai-memory        记忆（P1+SummarizingContextManager；P2+memory.vector）
cloud-ai-persona       人格（稳定）
cloud-ai-context       上下文段落（稳定）
cloud-ai-skills        技能（P2+SkillMatcher）
cloud-ai-security      安全（P2+异步审批+预设）
cloud-ai-execution     工具（P1+todo；P2+ask_user/interaction；P3+world/web/lsp）
cloud-ai-mcp [新,P2]   MCP 客户端 → 注册进 execution 的 ToolRegistry
  ↑
cloud-ai-runtime       循环（P1+event/session log/流式/并行/观测/预算）
cloud-ai-graph [新,P2] 图编排（agent/pattern/checkpoint，包装 runtime）
  ↑
cloud-ai-server        JDK HttpServer（P1+SSE；P2+审批/问答端点+会话持久化）
cloud-ai-spring        装配壳（P0 断线修复；P1+SSE+测试）
cloud-ai-example       示例（随各阶段扩充演示项）
```

依赖方向约束不变：core 零依赖；`graph`/`mcp` 与 `runtime` 平级，禁止逆向；新模块均需父 POM 注册 + `config` 静态工厂 + `@author cloud-ai`/`@since`。

---

## 10. 横切关注点

- **测试**：所有新行为遵循 BCDE（边界/正确/设计对齐/异常）；HTTP 一律 mock 或 Stub；`cloud-ai-spring` 是唯一允许 `@SpringBootTest` 的模块；事件流与图编排必须有序列快照测试防回归。
- **安全**：ask_user/审批/web/MCP 全部过 `SecurityInterceptor`；SessionLog 与日志不得含 api-key/凭证（P3-4 联动审计）；MCP 远端工具默认 `CUSTOM` 高风险档起步。
- **兼容性**：P1 所有新能力以 default 方法/可选 Builder 参数进入，既有 `run()`/配置行为不变（P0-4/P1-6 均默认关闭新路径）；`FinishStatus` 新增枚举值属附加分支。
- **文档**：每个 P 项落地时同步 README 与 openspec/specs；P0-6 之后 README 视为活文档。

---

## 11. 风险与取舍

| 风险 | 影响 | 缓解 |
|------|------|------|
| P1 事件流设计失误被 P2 大量依赖 | 返工面大 | 先以最小事件集落地，事件版本字段预留；deriveMessages 等价性测试锁定语义 |
| 图模块范围失控（对标 LangGraph 全集） | 交付延期 | 只做五模式 + checkpoint 子集，子图/DSL 导入明示不做 |
| MCP 协议演进快（dsh 尚在 developer preview 同理） | 适配返工 | 协议 DTO 与传输分离，锁最小方法集 |
| 同步审批改异步引入竞态 | 安全语义弱化 | InMemoryApprovalGateway 保持单写者语义；决策幂等键 = approvalId |
| 流式改造触碰循环骨架 | 回归风险 | `AgentLoopTemplate.run` 保持 final 与同步路径不动，流式走独立入口 `runStream` |
| 纯 JDK 双轨与 Spring 壳功能漂移（P0-3 已发生） | 两套入口行为不一致 | 每个传输层新端点在 server 与 spring 同步交付，上下文测试双向断言 |

---

## 12. 落地方式建议

1. **每个 P 项立一个 openspec 变更**：`openspec/changes/{cloud-ai-events|cloud-ai-streaming|cloud-ai-graph|...}/`（proposal/design/tasks/specs），沿用 `cloud-ai-security` 先例；本文档作为总纲不做任务勾选。
2. **建议的变更顺序**（依赖驱动）：
   `P0-4 → P1-1 → P1-2 → P1-3`（上下文主线）；`P0-1/P0-2/P0-3/P0-5/P0-6` 可随时并行插入；`P1-4~P1-8` 相互独立；P2 在 P1-1/P1-2 稳定后启动，`cloud-ai-graph` 拆三个变更分批合入。
3. **每个变更的交付物**：代码 + BCDE 测试 + README/openspec spec 更新 + Conventional Commits（`feat(runtime): ...`）。

---

## 13. 参考资料

- [Spring AI Alibaba 官方文档 — 概述](https://java2ai.com/docs/overview/)
- [什么是 Spring AI Alibaba Graph](https://java2ai.com/docs/1.0.0.2/tutorials/graph/whats-spring-ai-alibaba-graph/)
- [DeepSeek Harness 官方仓库](https://github.com/deepseek-ai/deepseek-harness)
- [DeepSeek Harness 中文教程（包结构导读）](https://github.com/ht426/deepseek-harness-tutorial)
- 本仓库：`openspec/changes/cloud-ai-security/`（变更流程先例）、`AGENTS.md`（编码与分包现行约定）
