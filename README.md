# Cloud AI

Java AI Agent 运行时框架（Agent Harness）。框架模块为**纯 JDK 实现**（`java.net.http.HttpClient`、Jackson、Reactor、Micrometer Observation、SLF4J），不依赖 Spring；配置装配通过各模块 `config` 包的静态工厂完成。Spring Boot 集成以可选模块 `cloud-ai-spring` 提供。

提供 LLM 适配、记忆管理、人格定义、上下文组装、技能加载、工具执行、安全治理和 Agent 编排的完整能力。

## 架构

```
cloud-ai-core       核心词汇表（chat/prompt/tool：ChatModel, Message, PromptSection, ToolCall, ContextManager）
  ↑
cloud-ai-llm        LLM 适配层（OpenAI, DeepSeek, Anthropic）、ChatClient、ModelRouter、Advisor、重试、观测、流式
cloud-ai-memory     记忆层（MemoryStore, MemoryRetriever, SimpleContextManager）
cloud-ai-persona    人格层（PersonaProvider, PersonaAssembler）
cloud-ai-context    环境上下文（EnvironmentInfo, AGENTS.md/CLAUDE.md 项目上下文）
cloud-ai-skills     技能层（SkillRegistry 渐进加载 + load_skill 工具）
cloud-ai-security   安全治理（PermissionManager, ApprovalGateway, AuditLogger）
cloud-ai-execution  工具执行（@Tool 注解注册 + JSON Schema 推导, ToolRegistry, 文件读写, Shell）
  ↑
cloud-ai-runtime    Agent 运行时（ReAct / PlanThenExecute, Session/Turn/Step 三级生命周期 SPI, 上下文压缩）
  ↑
cloud-ai-server     独立 HTTP 服务（JDK HttpServer + CloudAi 门面 Builder）
cloud-ai-spring     可选的 Spring Boot 自动装配集成层
cloud-ai-example    端到端示例（Stub LLM，无需 API Key）
```

## 模块

| 模块 | 说明 |
|------|------|
| `cloud-ai-core` | 核心模型与 SPI 接口（`ChatModel`、`ModelDiscovery`、`PromptAssembler`、`ContextManager`） |
| `cloud-ai-llm` | LLM 适配层（OpenAI / DeepSeek / Anthropic）、`ChatClient`、`ModelRouter`、重试、观测、流式聚合 |
| `cloud-ai-memory` | 记忆层：`MemoryStore`、`MemoryRetriever`（关键词检索）、`SimpleContextManager`（token 预算裁剪） |
| `cloud-ai-persona` | 人格层：`PersonaProvider`、`PersonaAssembler`，从配置加载 Agent 角色和行为约束 |
| `cloud-ai-context` | 上下文层：环境信息、AGENTS.md/CLAUDE.md 项目上下文段落 |
| `cloud-ai-skills` | 技能层：`FilesystemSkillRegistry` 扫描 `.agents/skills/`，`load_skill` 工具按需加载全文 |
| `cloud-ai-security` | 安全治理：权限校验（黑白名单 + Ant 路径）、审批流程（auto/manual 模式）、审计日志 |
| `cloud-ai-execution` | 工具执行：`@Tool`/`@ToolParam` 注解注册 + JSON Schema 自动推导、文件读写、Shell（可配置超时与工作目录） |
| `cloud-ai-runtime` | Agent 运行时：ReAct 与 PlanThenExecute 两种循环、三级生命周期 SPI、可选上下文压缩 |
| `cloud-ai-server` | 独立服务：JDK `HttpServer`、`CloudAi` 门面 Builder、`AgentService` |
| `cloud-ai-spring` | Spring Boot 4.1 自动装配集成层（可选，`cloud-ai.enabled` 控制，默认开启） |
| `cloud-ai-example` | 端到端示例 |

## 快速开始

### 门面 API（推荐）

```java
CloudAi agent = CloudAi.builder()
    .openai("https://api.openai.com/v1", apiKey, "gpt-4o")   // 或 .deepseek(...) / .model(custom)
    .tool("calculator", "Evaluate arithmetic", new MyCalculator())
    .persona("Math Assistant", "You are a helpful math assistant.")
    .workDir(Path.of("."))
    .build();

AgentResponse response = agent.run("calculate 25 * 4");
```

只需一行 `.openai(url, key, model)` 指定 LLM，其余各层自动串联。
可通过 Builder 覆盖任意一层：`.tool()`、`.persona()`、`.memory()`、`.maxTurns()`、`.timeout()` 等。

### 运行示例（无需 API Key）

```bash
mvn install -DskipTests
mvn compile exec:java -pl cloud-ai-example
```

示例使用 Stub LLM 模拟两轮 ReAct 对话，体验完整链路。

### 独立 HTTP 服务（纯 JDK）

```bash
mvn compile exec:java -pl cloud-ai-server -Dexec.mainClass=com.cloudai.server.CloudAiApplication
```

配置文件为 classpath 上的 `cloud-ai-server.properties`（可用外部 `./cloud-ai-server.properties` 覆盖，
支持 `${ENV_VAR:default}` 占位符）。关键配置：

```properties
server.port=8080
cloud-ai.server.api-key=
cloud-ai.llm.default-provider=openai
cloud-ai.llm.providers.openai.base-url=${OPENAI_BASE_URL:https://api.openai.com/v1}
cloud-ai.llm.providers.openai.api-key=${OPENAI_API_KEY:}
cloud-ai.llm.providers.openai.model=${OPENAI_MODEL:gpt-4o}
cloud-ai.agent.type=react
cloud-ai.agent.max-context-tokens=8000
cloud-ai.security.approval.mode=auto        # auto=低风险自动放行, manual=全部审批
cloud-ai.execution.shell.timeout=30s
cloud-ai.execution.filesystem.workspace=./workspace
```

#### HTTP API

```bash
# 运行 Agent
curl -X POST http://localhost:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"prompt":"帮我读取 /workspace/data.txt 的内容"}'

# 查询会话状态
curl http://localhost:8080/api/agent/status/{traceId}

# 中断运行中的会话
curl -X POST http://localhost:8080/api/agent/interrupt/{traceId}
```

### Spring Boot 集成（cloud-ai-spring）

引入 `cloud-ai-spring` 依赖后，通过 `cloud-ai.*` 命名空间配置（YAML）：

```yaml
cloud-ai:
  agent:
    type: react
    max-turns: 50
    timeout: 10m
    max-context-tokens: 8000
  llm:
    default-provider: openai
    providers:
      openai:
        base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
        api-key: ${OPENAI_API_KEY:}
        model: gpt-4o
        capabilities: [chat, tool_calling]
      anthropic:
        base-url: https://api.anthropic.com
        api-key: ${ANTHROPIC_API_KEY:}
        model: claude-sonnet-4
  skills:
    work-dir: .
  security:
    approval:
      mode: auto
      timeout: 120s
  execution:
    shell:
      timeout: 30s
    filesystem:
      workspace: ${CLOUD_AI_WORKSPACE:./workspace}
```

`@RestController /api/agent`（run/status/interrupt）与全部 Bean 均可替换（`@ConditionalOnMissingBean`）。

### 底层 API

```java
// 直接调用 LLM 层（流式）
ChatClient client = ChatClientFactory.create(router);
String reply = client.prompt("Hello").provider("openai").call().content();
client.prompt("Tell me a story").stream().content().subscribe(System.out::println);

// 通过 AgentLoop 运行完整 Agent 循环（含上下文压缩）
AgentLoop loop = AgentLoopFactory.builder(router, toolRegistry, execService)
    .type(AgentType.REACT)
    .maxTurns(50)
    .timeout(Duration.ofMinutes(10))
    .contextManager(new SimpleContextManager())  // 可选：超预算时裁剪请求历史
    .build();

AgentResponse response = loop.run(AgentRequest.of("帮我分析这个文件"));
```

### 自定义扩展

所有核心组件均为 SPI + `@ConditionalOnMissingBean`（Spring 路径），可直接替换默认实现：

```java
// Spring 路径：替换记忆存储为数据库实现
@Bean
public MemoryStore memoryStore(DataSource ds) {
    return new JdbcMemoryStore(ds);
}

// 纯 JDK 路径：实现 ChatModel / MemoryStore / ToolExecutor / ApprovalGateway 等接口，
// 通过 CloudAi.builder() 或各模块 config 工厂装配
```

## 构建

```bash
mvn clean package
```

Java 21 + Maven 多模块，测试使用 JUnit 5 + Mockito。

## 许可

Apache 2.0
