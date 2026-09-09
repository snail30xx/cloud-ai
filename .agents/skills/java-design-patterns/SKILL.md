---
name: java-design-patterns
description: Java 常用设计模式参考：策略、工厂、适配器、模板方法、责任链、观察者、装饰器、门面 — 包含适用场景、示例和反模式警示
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
// 策略接口
public interface PricingStrategy {
    BigDecimal calculate(Order order);
}

// 策略实现
@Component
public class VipPricingStrategy implements PricingStrategy { ... }

// 调度器
@Component
public class PricingDispatcher {
    private final Map<PricingType, PricingStrategy> strategyMap;

    public PricingDispatcher(List<PricingStrategy> strategies) {
        // 通过 Spring 注入所有策略实现
    }
}
```

### 适配器模式（Adapter）

- **场景**：外部系统接入 — 不同支付渠道、第三方 API 封装
- **结构**：目标接口 + 适配器 + 被适配者
- **约束**：适配器隔离外部依赖；内部使用统一领域模型，不泄露外部 DTO

```java
// 统一支付接口
public interface PaymentGateway {
    PaymentResult pay(PaymentRequest request);
}

// 适配器 — 封装第三方
@Component
public class AlipayAdapter implements PaymentGateway {
    private final AlipayClient client;

    @Override
    public PaymentResult pay(PaymentRequest request) {
        // 转换请求 → 调用第三方 → 转换响应
    }
}
```

### 工厂模式（Factory）

- **场景**：复杂对象创建 — 多态对象构建、配置驱动的实例化
- **约束**：优先使用静态工厂方法（`of()`, `from()`, `create()`）；复杂工厂再用抽象工厂

```java
// 静态工厂方法
public record User(String name, String email) {
    public static User fromRegisterRequest(RegisterRequest request) {
        return new User(request.name(), request.email());
    }
}

// 简单工厂
@Component
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

- **场景**：框架骨架 — 通用导入流程、审批流程
- **结构**：抽象父类定义骨架（`final`），子类实现变化步骤
- **约束**：骨架方法用 `final` 防止被覆写

```java
public abstract class AbstractDataImport {
    public final ImportResult execute(File file) {
        validate(file);              // 子类实现
        List<Row> rows = parse(file); // 子类实现
        return persist(rows);         // 通用逻辑
    }

    protected abstract void validate(File file);
    protected abstract List<Row> parse(File file);
    private ImportResult persist(List<Row> rows) { ... }
}
```

### 责任链（Chain of Responsibility）

- **场景**：请求处理链 — 校验链、拦截器链、审批链
- **约束**：每个节点独立可测试；链的组装通过配置或 SPI 注入

```java
public interface Validator {
    ValidationResult validate(Order order);
    void setNext(Validator next);
}

// Spring 自动装配
@Component
public class ValidationChain {
    private final List<Validator> validators; // 通过 @Order 控制顺序

    public ValidationChain(List<Validator> validators) {
        this.validators = validators;
    }
}
```

### 观察者/事件驱动（Observer）

- **场景**：领域事件 — 订单状态变更通知、数据同步
- **约束**：优先使用 Spring Events（`@EventListener`）或消息队列；事件应不可变（`record`）

```java
// 事件定义
public record OrderPaidEvent(Long orderId, BigDecimal amount, LocalDateTime paidAt) {}

// 事件发布
@Component
public class OrderService {
    private final ApplicationEventPublisher publisher;

    public void pay(Long orderId) {
        // 业务逻辑...
        publisher.publishEvent(new OrderPaidEvent(orderId, amount, LocalDateTime.now()));
    }
}

// 事件监听
@Component
public class SmsNotificationListener {
    @EventListener
    @Async
    public void onOrderPaid(OrderPaidEvent event) {
        // 发送短信通知
    }
}
```

### 装饰器（Decorator）

- **场景**：功能增强 — 缓存包装、日志包装、度量包装
- **约束**：与代理模式区分：装饰器运行时动态叠加功能；代理模式控制访问

### 门面模式（Facade）

- **场景**：子系统简化 — 为复杂子系统提供统一入口
- **约束**：门面不包含业务逻辑，只做委托；避免门面成为"上帝类"

## 反模式警示

| ❌ 反模式 | ✅ 正确做法 |
|-----------|------------|
| 单一实现创建接口 | 直接使用类，只在需要多态时提取接口 |
| 工具类堆积（`XxxUtils`, `XxxHelper`） | 将方法归属到对应的领域对象 |
| 过度泛型（`BaseDao<T, ID, Q, R>`） | 只有确实存在多个类型参数变化点时使用 |
| 继承滥用 | 优先组合；使用 `final` 限制非预期的继承 |
| 万能 Map（`Map<String, Object>`） | 定义明确的 DTO 或 Value Object |
| 保留"以备后用"代码 | 删除无用代码，Git 历史可恢复 |
