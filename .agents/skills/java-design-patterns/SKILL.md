---
name: java-design-patterns
description: Java 常用设计模式参考：策略、工厂、适配器、模板方法、责任链、观察者、装饰器、门面、状态机、管道、SPI 插件 — 包含适用场景、示例和反模式警示
user_invocable: true
---

# Java 设计模式参考

涉及架构设计、抽象解耦、代码重构时，参考以下模式。

## 核心原则

1. **仅在存在真实变化点、外部边界或重复业务规则时抽象**
2. **单一实现不要预先创建接口、抽象父类或通用框架**
3. **优先组合而非继承**
4. **模式必须降低耦合，否则直接实现**

## 常用模式速查

### 策略模式（Strategy）

- **场景**：可替换算法/规则 — 不同计费规则、校验策略、路由策略
- **结构**：策略接口 + 具体策略实现 + 上下文（调度器）
- **约束**：策略接口单一职责；避免策略类膨胀；用 `Map<Enum, Strategy>` 或 Spring `List<Strategy>` 注入调度

```java
public interface PricingStrategy {
    BigDecimal calculate(Order order);
}

@Component
public class VipPricingStrategy implements PricingStrategy { ... }

@Component
public class PricingDispatcher {
    private final Map<PricingType, PricingStrategy> strategyMap;

    public PricingDispatcher(List<PricingStrategy> strategies) {
        // 通过 Spring 注入所有策略实现
    }
}
```

### 适配器模式（Adapter）

- **场景**：外部系统接入 — 不同 LLM 厂商、支付渠道、第三方 API 封装
- **结构**：抽象基类 + 具体适配器 + 被适配者
- **约束**：适配器隔离外部依赖；内部使用统一领域模型，不泄露外部 DTO

```java
// 抽象基类 — 模板方法
public abstract class AbstractLlmAdapter implements ChatModel, ModelDiscovery {
    protected final RestClient restClient;
    protected final String provider;

    // 子类覆盖的模板方法
    protected void configureRestClient(RestClient.Builder builder) { ... }
    protected String getChatEndpoint() { return "/chat/completions"; }
    protected ChatResponse parseResponseInternal(Object rawResponse) { ... }
}

// 具体适配器 — 封装第三方
public class OpenAiLlmAdapter extends AbstractLlmAdapter { ... }
public class AnthropicLlmAdapter extends AbstractLlmAdapter { ... }
```

### 工厂模式（Factory）

- **场景**：复杂对象创建 — 多态对象构建、配置驱动的实例化
- **约束**：优先使用静态工厂方法（`of()`, `from()`, `create()`）；复杂工厂再用抽象工厂

```java
public record User(String name, String email) {
    public static User fromRegisterRequest(RegisterRequest request) {
        return new User(request.name(), request.email());
    }
}

// 简单工厂 + switch 表达式
public class DocumentParserFactory {
    public DocumentParser create(DocumentType type) {
        return switch (type) {
            case PDF -> new PdfParser();
            case WORD -> new WordParser();
        };
    }
}
```

### 模板方法（Template Method）

- **场景**：框架骨架 — Agent 循环、通用导入流程、审批流程
- **结构**：抽象父类定义骨架（`final`），子类实现变化步骤
- **约束**：骨架方法用 `final` 防止被覆写；策略通过 SPI 接口注入

```java
public abstract class AgentLoopTemplate implements AgentLoop {

    // final 骨架方法，不可覆写
    public final AgentResponse run(AgentRequest request) {
        var session = sessionLifecycle().createSession(request);
        sessionLifecycle().onSessionStart(session, request);
        // ... 循环骨架
    }

    // 策略注入点 — 子类实现
    protected abstract SessionLifecycle sessionLifecycle();
    protected abstract TurnLifecycle turnLifecycle();
    protected abstract StepLifecycle stepLifecycle();
}

// 具体实现 — 通过实现三个生命周期接口提供策略
public class ReActAgentLoop extends AgentLoopTemplate
        implements SessionLifecycle, TurnLifecycle, StepLifecycle { ... }
```

### 责任链（Chain of Responsibility）

- **场景**：请求处理链 — Advisor 链、校验链、拦截器链、审批链
- **约束**：每个节点独立可测试；链的组装通过配置或 SPI 注入

```java
@FunctionalInterface
public interface Advisor {
    ChatRequest advise(ChatRequest request, AdvisorChain chain);

    interface AdvisorChain {
        ChatRequest next(ChatRequest request);
    }
}
```

### 观察者/事件驱动（Observer）

- **场景**：领域事件 — 订单状态变更通知、数据同步
- **约束**：优先使用 Spring Events（`@EventListener`）或消息队列；事件应不可变（`record`）

```java
public record OrderPaidEvent(Long orderId, BigDecimal amount, LocalDateTime paidAt) {}

@Component
public class OrderService {
    private final ApplicationEventPublisher publisher;

    public void pay(Long orderId) {
        publisher.publishEvent(new OrderPaidEvent(orderId, amount, LocalDateTime.now()));
    }
}

@Component
public class SmsNotificationListener {
    @EventListener
    @Async
    public void onOrderPaid(OrderPaidEvent event) { ... }
}
```

### 装饰器（Decorator）

- **场景**：功能增强 — 缓存包装、日志包装、度量包装
- **约束**：与代理模式区分：装饰器运行时动态叠加功能；代理模式控制访问

### 门面模式（Facade）

- **场景**：子系统简化 — 为复杂子系统提供统一入口
- **约束**：门面不包含业务逻辑，只做委托；避免门面成为"上帝类"

## AI 框架常用模式

### 状态机模式（State Machine）

- **场景**：Agent 循环状态转换 — 会话生命周期、工具执行审批流、Agent 运行状态
- **结构**：状态枚举 + 状态转换规则 + 上下文
- **约束**：状态转换逻辑集中管理；非法转换抛异常而非静默忽略

```java
// 会话状态
public enum AgentSessionStatus { CREATED, RUNNING, INTERRUPTED, COMPLETED, FAILED }

// 运行结果状态
public enum FinishStatus { COMPLETED, STOP_CONDITION, INTERRUPTED, ERROR }

// 会话上下文 — 持有当前状态
public class AgentSession {
    private volatile AgentSessionStatus status = AgentSessionStatus.CREATED;

    public void transitionTo(AgentSessionStatus next) {
        // 校验合法转换
        if (!isValidTransition(this.status, next)) {
            throw new IllegalStateException(
                "Illegal transition: " + status + " -> " + next);
        }
        this.status = next;
    }
}
```

### 管道模式（Pipeline）

- **场景**：请求处理管道 — Advisor 链、请求预处理/后处理、流式响应聚合
- **结构**：管道接口 + 处理器列表 + 管道执行器
- **约束**：每个处理器独立可测试；管道顺序通过 `@Order` 或配置控制

```java
// 管道处理器
@FunctionalInterface
public interface Advisor {
    ChatRequest advise(ChatRequest request, AdvisorChain chain);

    interface AdvisorChain {
        ChatRequest next(ChatRequest request);
    }
}

// 管道组装 — ChatClientFactory 负责组装 Advisor 链
ChatClient client = ChatClientFactory.builder(router)
    .defaultAdvisors(
        new PersonaAdvisor(personaProvider),     // 请求前注入人设
        new MemoryAdvisor(memoryRetriever),      // 请求前注入记忆
        new LoggingAdvisor()                     // 请求前后记录日志
    )
    .build();
```

### SPI 插件模式（Service Provider Interface）

- **场景**：可替换组件 — 模型适配器、工具执行器、记忆存储、技能加载器
- **结构**：SPI 接口 + 默认实现 + 注册器/发现器 + 自动配置
- **约束**：接口放 `spi/` 包；默认实现放 `impl/` 包；自动配置用 `@ConditionalOnMissingBean` 允许替换

```java
// SPI 接口 — 最窄契约
public interface ChatModel {
    ChatResponse call(ChatRequest request);
    default Flux<ChatResponse> stream(ChatRequest request) {
        return Flux.just(call(request));
    }
}

// 默认实现
public class OpenAiLlmAdapter extends AbstractLlmAdapter { ... }

// 注册器 — 运行时发现和路由
public class ModelRouter {
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();

    public void register(String name, ChatModel model) {
        models.put(name, model);
    }

    public ChatResponse chat(String provider, ChatRequest request) {
        var model = models.get(provider);
        if (model == null) {
            throw new IllegalStateException("Provider not registered: " + provider);
        }
        return model.call(request);
    }
}

// 自动配置 — 创建并注册默认实现
@Configuration
public class LlmAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ModelRouter modelRouter() {
        var router = new ModelRouter(defaultProvider);
        for (var entry : providers.entrySet()) {
            router.register(entry.getKey(), createAdapter(entry.getValue()));
        }
        return router;
    }
}
```

## 反模式警示

| 反模式 | 正确做法 |
|--------|----------|
| 单一实现创建接口 | 直接使用类，只在需要多态时提取接口 |
| 工具类堆积（`XxxUtils`, `XxxHelper`） | 将方法归属到对应的领域对象 |
| 过度泛型（`BaseDao<T, ID, Q, R>`） | 只有确实存在多个类型参数变化点时使用 |
| 继承滥用 | 优先组合；使用 `final` 限制非预期的继承 |
| 万能 Map（`Map<String, Object>`） | 定义明确的 DTO 或 Value Object |
| 保留"以备后用"代码 | 删除无用代码，Git 历史可恢复 |
| 适配器泄露外部 DTO | 适配器内部转换为统一领域模型 |
| 管道处理器包含业务逻辑 | 处理器只做横切关注点（日志、监控、增强），业务逻辑放 Service |
| SPI 接口包含实现细节 | SPI 只定义最窄契约，实现细节放 `impl/` |
| 状态转换无校验 | 集中管理合法转换，非法转换抛异常 |
