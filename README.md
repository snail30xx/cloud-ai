# Cloud AI

Java AI Agent 运行时框架，基于 Spring Boot 4.1 构建。提供 LLM 适配、记忆管理、人格定义、工具执行、安全治理和 Agent 编排的完整七层架构。

## 架构

```
cloud-ai-core       核心模型与 SPI（ChatModel, ModelDiscovery, Message, ToolCall）
  ↑
cloud-ai-llm        LLM 适配层（OpenAI, DeepSeek, Anthropic）、ChatClient、ModelRouter、重试、观测、流式
  ↑
cloud-ai-memory     记忆层（MemoryStore, MemoryRetriever, ContextManager）
cloud-ai-persona    人格层（PersonaProvider, PersonaAssembler）
  ↑
cloud-ai-security   安全治理（PermissionManager, ApprovalGateway, AuditLogger）
  ↑
cloud-ai-execution  工具执行（ToolExecutor, ToolRegistry, 文件读写, Shell）
  ↑
cloud-ai-runtime    Agent 运行时（ReAct, PlanThenExecute, AgentLoop, 生命周期 SPI）
  ↑
cloud-ai-server     Spring Boot 应用入口 + HTTP API
```

## 模块

| 模块 | 说明 |
|------|------|
| `cloud-ai-core` | 核心模型与 SPI 接口（`ChatModel`, `ModelDiscovery`） |
| `cloud-ai-llm` | LLM 适配层（OpenAI, DeepSeek, Anthropic）、ChatClient、ModelRouter、重试、观测、流式聚合 |
| `cloud-ai-memory` | 记忆层：`MemoryStore`、`MemoryRetriever`、`ContextManager`，支持关键词检索和上下文压缩 |
| `cloud-ai-persona` | 人格层：`PersonaProvider`、`PersonaAssembler`，从配置加载 Agent 角色和行为约束 |
| `cloud-ai-security` | 安全治理：权限校验、审批流程（Human-in-the-Loop）、审计日志 |
| `cloud-ai-execution` | 工具执行：`ToolExecutor`、`ToolRegistry`、文件读写、Shell 命令执行 |
| `cloud-ai-runtime` | Agent 运行时：ReAct 和 PlanThenExecute 两种循环、Session/Turn/Step 三级生命周期 SPI |
| `cloud-ai-server` | Spring Boot 启动模块 + Agent HTTP API |

## 快速开始

### 配置

```yaml
cloud-ai:
  agent:
    type: react
    max-turns: 50
    timeout: 10m
  llm:
    default-provider: openai
    providers:
      openai:
        base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
        api-key: ${OPENAI_API_KEY:}
        model: gpt-4o
        timeout: 60s
        capabilities: [chat, tool_calling]
      deepseek:
        base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
        api-key: ${DEEPSEEK_API_KEY:}
        model: deepseek-v4-pro
        timeout: 120s
        capabilities: [chat, tool_calling, thinking]
  persona:
    default-persona-id: default
    personas:
      default:
        name: Cloud AI Assistant
        role: AI assistant
        system-prompt: You are a helpful AI assistant.
        guidelines:
          - Always be accurate and honest
          - Ask for clarification when the request is ambiguous
        tone-style: professional and concise
        constraints:
          - Never share sensitive data or credentials
          - Do not execute destructive operations without confirmation
  memory:
    max-context-tokens: 8000
    max-retrieval-results: 5
  execution:
    filesystem:
      workspace: ${CLOUD_AI_WORKSPACE:./workspace}
    shell:
      timeout: 30s
  security:
    approval:
      mode: auto
      timeout: 120s
```

### HTTP API

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

### 快速接入

```java
// 接入 OpenAI
CloudAi agent = CloudAi.builder()
    .openai("https://api.openai.com/v1", apiKey, "gpt-4o")
    .tool("calculator", "Evaluate arithmetic", new MyCalculator())
    .persona("Math Assistant", "You are a helpful math assistant.")
    .build();

AgentResponse response = agent.run("calculate 25 * 4");
```

```java
// 接入 DeepSeek
CloudAi agent = CloudAi.builder()
    .deepseek("https://api.deepseek.com", apiKey, "deepseek-v4-pro")
    .build();
```

只需一行 `.openai(url, key, model)` 或 `.deepseek(url, key, model)` 指定 LLM，其余七层自动串联。
可通过 Builder 覆盖任意一层：`.tool()`、`.persona()`、`.memory()`、`.maxTurns()`、`.timeout()` 等。

### HTTP API

```bash
# 运行 Agent
curl -X POST http://localhost:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d "{\"prompt\":\"帮我读取 /workspace/data.txt 的内容\"}"

# 查询会话状态
curl http://localhost:8080/api/agent/status/{traceId}

# 中断运行中的会话
curl -X POST http://localhost:8080/api/agent/interrupt/{traceId}
```

### 运行示例

```bash
mvn install -DskipTests
mvn compile exec:java -pl cloud-ai-example
```

示例使用 Stub LLM 模拟两轮 ReAct 对话，无需 API Key 即可体验完整七层链路。

### 底层 API

```java
// 创建 ChatClient（LLM 层直接调用）
ChatClient client = ChatClientFactory.create(router);

String reply = client.prompt("Hello")
    .provider("openai")
    .call()
    .content();

// 通过 AgentLoop 运行完整 Agent 循环
AgentLoop loop = AgentLoopFactory.builder(router, toolRegistry, execService)
    .type(AgentType.REACT)
    .maxTurns(50)
    .timeout(Duration.ofMinutes(10))
    .build();

AgentResponse response = loop.run(AgentRequest.of("帮我分析这个文件"));
```

```java
// 创建 ChatClient（LLM 层直接调用）
ChatClient client = ChatClientFactory.create(router);

String reply = client.prompt("Hello")
    .provider("openai")
    .call()
    .content();

// 流式调用
client.prompt("Tell me a story")
    .stream()
    .content()
    .subscribe(System.out::println);
```

```java
// 通过 AgentLoop 运行完整 Agent 循环
AgentLoop loop = AgentLoopFactory.builder(router, toolRegistry, execService)
    .type(AgentType.REACT)
    .maxTurns(50)
    .timeout(Duration.ofMinutes(10))
    .build();

AgentResponse response = loop.run(AgentRequest.of("帮我分析这个文件"));
```

### 自定义扩展

所有核心组件均为 SPI + `@ConditionalOnMissingBean`，可通过 Spring Bean 替换默认实现：

```java
// 替换记忆存储为数据库实现
@Bean
public MemoryStore memoryStore(DataSource ds) {
    return new JdbcMemoryStore(ds);
}

// 替换人格提供者为外部系统
@Bean
public PersonaProvider personaProvider() {
    return new RemotePersonaProvider("https://config-service/api/personas");
}

// 自定义观测
@Component
public class CustomObservationConvention
        implements ObservationConvention<ChatModelObservationContext> {
    @Override
    public KeyValues getLowCardinalityKeyValues(ChatModelObservationContext ctx) {
        return KeyValues.of("tenant", currentTenant());
    }
}
```

## 构建

```bash
mvn clean package
```

Java 21 + Maven 多模块，测试使用 JUnit 5 + Mockito。

## 许可

Apache 2.0

