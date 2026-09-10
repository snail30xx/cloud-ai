---
name: java-ai-framework
description: cloud-ai 框架级开发规范：模块结构、SPI 设计、适配器模式、Advisor 链、自动配置、异常体系、观测性 — 用于新增模块、设计接口、编写适配器或审查架构
user_invocable: true
---

# cloud-ai 框架级开发规范

编写或审查框架级代码时（新增模块、设计 SPI 接口、编写适配器或 Advisor、自动配置），应用以下规范。所有规范基于现有代码，不假设未实现的能力。

## 1. 模块结构与命名

### 模块命名

```
cloud-ai-{component}
```

现有模块：

| 模块 | 职责 |
|------|------|
| `cloud-ai-core` | 最窄契约：消息/模型/工具调用数据模型 + SPI 接口（ChatModel, PromptAssembler） |
| `cloud-ai-llm` | 模型适配层：ModelRouter + 各厂商 Adapter + ChatClient + Advisor + Observation |
| `cloud-ai-memory` | 对话记忆：MemoryStore + MemoryRetriever + ContextManager + MemoryAdvisor |
| `cloud-ai-persona` | 人设管理：PersonaProvider + PersonaAssembler + PersonaAdvisor |
| `cloud-ai-context` | 环境上下文：ContextProvider + EnvironmentInfo |
| `cloud-ai-skills` | 技能注册：SkillRegistry + SkillLoader + SkillExecutor |
| `cloud-ai-security` | 安全审批：PermissionManager + ApprovalGateway + AuditLogger + SecurityInterceptor |
| `cloud-ai-execution` | 工具执行：ToolRegistry + ToolExecutor + ToolExecutionService |
| `cloud-ai-runtime` | Agent 循环：AgentLoop SPI + ReActAgentLoop + PlanThenExecuteAgentLoop |
| `cloud-ai-server` | HTTP 入口：Controller + Service + Facade + DTO |
| `cloud-ai-example` | 示例：ExampleApplication + StubChatModel |

### 依赖方向

```
core ← llm ← runtime ← server
              ↑
memory, persona, context, skills, security, execution
```

- `core` 不依赖任何其他模块
- `llm` 依赖 `core`
- `runtime` 依赖 `llm`、`execution`、`security`（通过接口）
- `memory`、`persona`、`context`、`skills`、`security`、`execution` 依赖 `core`
- `server` 依赖 `runtime` 和需要的功能模块
- 禁止逆向依赖或跨层依赖

## 2. 包结构（四层约定）

每个模块统一使用四层子包：

```
com.cloudai.{module}.spi      → 服务接口/抽象（ChatModel, MemoryStore, AgentLoop...）
com.cloudai.{module}.impl     → 默认实现（DefaultXxx, InMemoryXxx...）
com.cloudai.{module}.model    → 数据对象（record / enum）
com.cloudai.{module}.config   → Spring Boot 自动配置（XxxAutoConfiguration + XxxProperties）
```

可选子包（按需）：

```
com.cloudai.{module}.adapter    → 外部系统适配器（如 LlmAdapter）
com.cloudai.{module}.advisor     → 请求拦截器（如 Advisor 实现）
com.cloudai.{module}.client      → 高级 API（如 ChatClient）
com.cloudai.{module}.exception   → 模块异常体系
com.cloudai.{module}.observation → Micrometer 观测
com.cloudai.{module}.retry       → 重试机制
com.cloudai.{module}.stream      → 流式响应处理
com.cloudai.{module}.interceptor → HTTP 拦截器
```

## 3. SPI 设计规范

### 接口放 `spi/`，实现放 `impl/`

```java
// spi/ChatModel.java — 接口
public interface ChatModel {
    ChatResponse call(ChatRequest request);
    default Flux<ChatResponse> stream(ChatRequest request) {
        return Flux.just(call(request));
    }
}

// impl/InMemoryMemoryStore.java — 默认实现以 Default 或 InMemory 前缀
public class InMemoryMemoryStore implements MemoryStore { ... }
```

### 接口设计原则

- 接口单一职责，只包含运行时调用路径的最窄契约
- 可选能力用 `default` 方法提供回退实现（如 `stream` 默认回退到 `call`）
- 接口标注 `@FunctionalInterface` 当且仅当只有一个抽象方法（如 `Advisor`、`ToolExecutor`）
- 接口的 Javadoc 必须说明契约语义、线程安全性和扩展方式

### SPI 注册与发现

- 模型适配器通过 `ModelRouter.register(name, ChatModel)` 注册
- 工具执行器通过 `ToolRegistry` 注册发现
- 技能通过 `SkillRegistry` 注册发现
- 自动配置类负责创建并注册默认实现，用 `@ConditionalOnMissingBean` 允许替换

## 4. 适配器模式（Adapter）

### 抽象基类 + 具体适配器

```java
// adapter/AbstractLlmAdapter.java — 模板方法基类
public abstract class AbstractLlmAdapter implements ChatModel, ModelDiscovery {

    protected final RestClient restClient;
    protected final String provider;
    protected final String model;
    protected final ProviderProperties props;

    // 子类覆盖的模板方法
    protected void configureRestClient(RestClient.Builder builder) { ... }
    protected String getChatEndpoint() { return "/chat/completions"; }
    protected Class<?> getChatResponseType() { return OpenAiChatResponse.class; }
    protected Object buildRequestBodyInternal(ChatRequest request) { ... }
    protected ChatResponse parseResponseInternal(Object rawResponse) { ... }
    @Nullable
    protected ChatResponse parseSseLineInternal(String data) throws Exception { ... }
}

// adapter/OpenAiLlmAdapter.java — 具体适配器
public class OpenAiLlmAdapter extends AbstractLlmAdapter { ... }
```

### 适配器规范

- 适配器实现 `ChatModel`（运行时调用）和可选的 `ModelDiscovery`（模型发现）
- HTTP 调用使用 `RestClient`，不使用 `RestTemplate`
- 超时通过 `SimpleClientHttpRequestFactory` 的 `setConnectTimeout` / `setReadTimeout` 设置
- 认证 header 通过覆盖 `configureRestClient` 添加
- 流式响应用 `Flux<ChatResponse>` + SSE 逐行解析
- 适配器内部不泄露外部 DTO，转换为统一领域模型（`ChatResponse`、`TokenUsage`、`ToolCall`）

## 5. Advisor 链（请求拦截器）

### 接口定义

```java
@FunctionalInterface
public interface Advisor {
    ChatRequest advise(ChatRequest request, AdvisorChain chain);

    interface AdvisorChain {
        ChatRequest next(ChatRequest request);
    }
}
```

### Advisor 规范

- Advisor 定义在所属模块（如 `PersonaAdvisor` 在 `persona`，`MemoryAdvisor` 在 `memory`）
- Advisor 通过 `ChatClientFactory.builder().defaultAdvisors(...)` 组装
- Advisor 只修改请求，不直接调用 LLM
- Advisor 实现应保持无状态或线程安全

## 6. 自动配置规范

### 模式

```java
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
@ConditionalOnProperty(name = "cloud-ai.llm.enabled", havingValue = "true", matchIfMissing = true)
public class LlmAutoConfiguration {

    public LlmAutoConfiguration(LlmProperties props,
                                ObjectProvider<ObservationRegistry> registry) { ... }

    @Bean
    @ConditionalOnMissingBean
    public ModelRouter modelRouter() { ... }
}
```

### 规范

- 每个模块一个 `XxxAutoConfiguration` + `XxxProperties`
- `@ConditionalOnProperty` 控制开关，`matchIfMissing = true` 表示默认启用
- `@ConditionalOnMissingBean` 允许使用者替换默认实现
- 可选依赖用 `ObjectProvider` 注入，`getIfAvailable` 提供回退
- 配置前缀统一 `cloud-ai.{module}.*`（如 `cloud-ai.llm.providers`）

### Properties 规范

- 优先使用 `record` 定义 Properties（如 `ProviderProperties`）
- 必须提供 `validate()` 方法做启动时校验
- 校验失败抛 `IllegalStateException`，错误信息包含配置 key 和原因

## 7. 异常体系

### 层级结构

```
RuntimeException
  └── LlmException（模块基类，含 isRetryable()）
        ├── LlmAuthException        — 401/403，不可重试
        ├── LlmClientException      — 4xx，不可重试
        ├── LlmServerException      — 5xx，可重试
        ├── LlmRateLimitException   — 429，可重试
        └── LlmTimeoutException     — 超时，可重试
```

### 规范

- 模块级基类继承 `RuntimeException`，提供 `isRetryable()` 判断
- 每个异常包含 provider 名称、HTTP 状态码和错误消息
- HTTP 状态码映射通过 `onStatus` 处理，不依赖异常解析
- 异常信息不泄露敏感信息（API Key、内部 URL）

## 8. 模板方法（Agent Loop）

### 骨架 + 策略

```java
public abstract class AgentLoopTemplate implements AgentLoop {

    // final 骨架方法，不可覆写
    public final AgentResponse run(AgentRequest request) {
        var session = sessionLifecycle().createSession(request);
        sessionLifecycle().onSessionStart(session, request);
        // ... 循环骨架
    }

    // 策略注入点
    protected abstract SessionLifecycle sessionLifecycle();
    protected abstract TurnLifecycle turnLifecycle();
    protected abstract StepLifecycle stepLifecycle();
}
```

### 规范

- 骨架方法标注 `final`
- 策略通过 SPI 接口注入（`SessionLifecycle`、`TurnLifecycle`、`StepLifecycle`）
- 每个策略接口职责单一
- 新增 Agent 类型时继承 `AgentLoopTemplate` 并实现三个生命周期接口

## 9. 观测性

```java
var ctx = new ChatModelObservationContext(provider, model, request, isStream);
var observation = Observation.createNotStarted(
        DefaultChatModelObservationConvention.OBSERVATION_NAME,
        () -> ctx, observationRegistry)
        .observationConvention(observationConvention);

return observation.observe(() -> { /* 业务逻辑 */ });
```

### 规范

- 每个外部调用（LLM 调用、工具执行）创建 Observation
- Observation Convention 放 `observation/` 子包
- 流式调用在 `doFinally` 中 `stop()`，在 `doOnError` 中 `error()`
- ObservationRegistry 可为 `NOOP`（当 Micrometer 未配置时）

## 10. 测试辅助方法

```java
// package-private，以 ForTest 后缀命名
ChatResponse parseResponseForTest(OpenAiChatResponse response) {
    return parseResponse(response);
}
```

### 规范

- 测试需要访问的内部方法用 package-private 可见性
- 方法名以 `ForTest` 后缀结尾
- 不暴露为 public API
- Javadoc 标注 `/** package-private test helper */`

## 11. 框架级审查清单

新增模块或修改框架级代码时，逐项检查：

- [ ] 模块名遵循 `cloud-ai-{component}` 约定
- [ ] 包结构遵循 `spi/impl/model/config` 四层
- [ ] SPI 接口放 `spi/`，默认实现放 `impl/` 并以 `Default`/`InMemory` 前缀命名
- [ ] 依赖方向正确（不逆向、不跨层）
- [ ] 自动配置有 `@ConditionalOnMissingBean` 允许替换
- [ ] Properties 有 `validate()` 启动时校验
- [ ] 外部调用设置了连接超时和读取超时
- [ ] HTTP 调用使用 `RestClient`，不用 `RestTemplate`
- [ ] 流式响应有背压处理和 `doFinally` 清理
- [ ] 异常体系有模块基类 + 子类，区分可重试与不可重试
- [ ] 观测性覆盖所有外部调用
- [ ] 模板方法骨架标注 `final`
- [ ] 测试辅助方法用 package-private + `ForTest` 后缀
- [ ] 所有类有 `@author cloud-ai` 和 `@since 1.0` Javadoc
- [ ] 不泄露外部 DTO 到领域模型
