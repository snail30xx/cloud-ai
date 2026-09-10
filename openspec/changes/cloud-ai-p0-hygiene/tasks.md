# cloud-ai-p0-hygiene — Tasks

## Phase 1: Anthropic 适配器补全（cloud-ai-llm）

- [x] **Task 1.1**: 请求映射 — system 提取为顶层 `system` 字段；tools → `input_schema`；
  assistant ToolCall → `tool_use` 块（input 为对象，非法 JSON 降级空对象）；tool 消息 → `tool_result` 块；
  ModelOptions 透传 temperature/top_p/stop_sequences/max_tokens；空 tools 不发送字段
- [x] **Task 1.2**: 响应解析 — `tool_use` 块 → `ToolCall`（input 序列化 JSON 字符串）；
  stop_reason 补 stop_sequence 映射；空 content 边界
- [x] **Task 1.3**: `LlmAutoConfiguration.createAdapter` 新增 anthropic 分支（名称或 capabilities），
  方法改 package-private 供测试
- [x] **Task 1.4**: 测试 — 重写 `AnthropicAdapterTest` 为协议映射单测；新增 `LlmAutoConfigurationTest`
- 验证：`mvn test -pl cloud-ai-llm -am`

## Phase 2: ContextManager 接线（core + memory + runtime）

- [x] **Task 2.1**: `ContextManager` 接口迁移至 `com.cloudai.core.chat`（共享词汇表归 core）
- [x] **Task 2.2**: `SimpleContextManager` / memory 工厂 / spring 装配 / 既有测试 import 同步
- [x] **Task 2.3**: `AgentLoopBuilder` 增加 `contextManager` / `maxContextTokens`；
  `ReActAgentLoop`、`PlanThenExecuteAgentLoop` 新增兼容构造器；`callModel` 压缩请求视图
- [x] **Task 2.4**: `RuntimeAutoConfiguration` 新工厂重载（旧签名保留委托）
- [x] **Task 2.5**: 测试 — 新增 `ContextCompressionTest`（裁剪 / system 保留 / 会话历史不变 / 未配置兼容 / 空历史边界）
- 验证：`mvn test -pl cloud-ai-core,cloud-ai-memory,cloud-ai-runtime -am`

## Phase 3: 悬空配置清理（execution + security）

- [x] **Task 3.1**: `ExecutionProperties` 重定义（移除 fileDeleteEnabled；新增 shellTimeout / workspace）
- [x] **Task 3.2**: `ShellToolExecutor` 超时可配置 + 修复"先读尽输出再 waitFor"导致的超时失效 bug
  （后台线程读输出，主线程超时判定）
- [x] **Task 3.3**: `FileWriteExecutor` 补 baseDir 构造器（workspace 相对路径解析）
- [x] **Task 3.4**: `ApprovalMode` 枚举 + `InMemoryApprovalGateway` 模式支持 +
  `SecurityProperties.Approval.mode` 绑定（非法值 fail fast）
- [x] **Task 3.5**: 测试 — `ExecutionAutoConfigurationTest`、`ShellToolExecutorTest.Timeout`、
  `FileWriteExecutorTest.WorkspaceResolution`、`InMemoryApprovalGatewayTest.ApprovalModeBehavior`、
  `SecurityAutoConfigurationApprovalModeTest`
- 验证：`mvn test -pl cloud-ai-security,cloud-ai-execution -am`

## Phase 4: Spring 路径修复（cloud-ai-spring）

- [x] **Task 4.1**: `SkillsAutoConfiguration` 注册 `load_skill` 进 ToolRegistry（无技能不注册）
- [x] **Task 4.2**: `ServerAutoConfiguration` — AgentService 注入 SkillRegistry（ObjectProvider）；
  修复 ApiKeyInterceptor 构造器自引用循环依赖
- [x] **Task 4.3**: `LlmAutoConfiguration` — ObservationRegistry 改可选注入（ObjectProvider）
- [x] **Task 4.4**: `CloudAiProperties` — 组件 `skill`→`skills`；多构造器 record 加
  `@ConstructorBinding` + `@DefaultValue`；Execution/Security.Approval 组件对齐
- [x] **Task 4.5**: `RuntimeAutoConfiguration` 接线 ContextManager + maxContextTokens（均 ObjectProvider 可选）
- [x] **Task 4.6**: POM 覆盖 logback 至 1.5.34（对齐 Boot 4.1 BOM）
- [x] **Task 4.7**: 模块首批上下文测试 — 默认装配 / 有技能注册 load_skill / skills 禁用
- 验证：`mvn test -pl cloud-ai-spring -am`

## Phase 5: 独立服务器修复（cloud-ai-server）

- [x] **Task 5.1**: 配置文件改为 `cloud-ai-server.properties`（避开 application.* 命名），
  删除 application.yml（含其无绑定的 sandbox/management/spring 键）
- [x] **Task 5.2**: `CloudAiApplication` 重写 — 占位符解析 / 时长解析 / provider 聚合 /
  九层装配（支持 modelOverride 测试注入）/ HttpServer 路由 / 保活与停机
- [x] **Task 5.3**: 安全默认与 facade 对齐（FILE_READ(**)、workspace 内 FILE_WRITE 放行并告警）
- [x] **Task 5.4**: 测试 — `CloudAiApplicationTest`（parseDuration 参数化 / 占位符 / provider 聚合 /
  端到端 HTTP + Stub 模型）
- 验证：`mvn test -pl cloud-ai-server -am`

## Phase 6: 文档对齐

- [x] **Task 6.1**: README 重写（12 模块、纯 JDK 定位、门面/独立服务/Spring 三种用法、去重）
- [x] **Task 6.2**: `openspec/project.md` 更新（领域分包约定、现行依赖图、关键约定）
- 验证：人工核对与代码一致

## 最终验证

- [x] `mvn test` 12 模块全量通过
