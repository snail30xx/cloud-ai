---
name: java-spring-boot
description: Spring Boot 4.x 最佳实践：分层架构、构造器注入、自动配置、异常处理、配置管理和外部调用；版本以项目 POM 为准
user_invocable: true
---

# Spring Boot 4.x 最佳实践

项目 POM 已引入 Spring Boot 4.1.0；以下实践可直接应用。具体 API 和默认行为以项目声明的版本为准。

## 1. 分层架构（DDD 风格）

```
controller/     ← REST 接口层：协议转换、参数校验、响应封装
  ↓
service/        ← 业务逻辑层：业务编排、事务管理
  ├── impl/    ← Service 实现类（以 Impl 结尾）
  ↓
domain/         ← 领域层：核心业务规则、领域服务、值对象
  ↓
repository/     ← 数据访问层：DAO、Mapper、数据持久化
```

### 严格约束

- Controller 只做协议转换、校验和响应，不包含业务逻辑
- Service 做业务编排，复杂核心规则提取为领域对象或领域服务
- 调用链：`controller → service → repository/client`，不可逆向或跨层
- 禁止在 Controller 返回 Entity/DAO 对象，必须转为 VO/DTO

## 2. 依赖注入

```java
// ✅ 推荐：构造器注入（Lombok）
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
}

// ✅ 推荐：构造器注入（显式构造器，项目实际使用的方式）
@Service
public class OrderService {
    private final OrderRepository orderRepo;
    private final PaymentGateway paymentGateway;

    public OrderService(OrderRepository orderRepo, PaymentGateway paymentGateway) {
        this.orderRepo = orderRepo;
        this.paymentGateway = paymentGateway;
    }
}

// ❌ 禁止：字段注入
@Service
public class UserService {
    @Autowired  // ❌ 不允许
    private UserRepository userRepository;
}
```

### 可选依赖注入

```java
// 使用 ObjectProvider 注入可选 Bean，提供回退
public LlmAutoConfiguration(LlmProperties props,
        ObjectProvider<ObservationRegistry> registryProvider,
        ObjectProvider<ObservationConvention<ChatModelObservationContext>> conventionProvider) {
    this.observationRegistry = registryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
    this.observationConvention = conventionProvider.getIfAvailable();
}
```

## 3. 自动配置模式

项目实际使用 `@Configuration` + `@ConditionalOnProperty` + `@ConditionalOnMissingBean` 模式：

```java
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
@ConditionalOnProperty(name = "cloud-ai.llm.enabled", havingValue = "true", matchIfMissing = true)
public class LlmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ModelRouter modelRouter() { ... }
}
```

### 规范

- 每个模块一个 `XxxAutoConfiguration` + `XxxProperties`
- `@ConditionalOnProperty` 控制开关，`matchIfMissing = true` 默认启用
- `@ConditionalOnMissingBean` 允许使用者替换默认实现
- 可选依赖用 `ObjectProvider` 注入，`getIfAvailable` 提供回退
- 配置前缀统一 `cloud-ai.{module}.*`
- Properties 优先使用 `record`，必须提供 `validate()` 启动时校验

```java
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    String gatewayUrl,
    int connectTimeout,
    int readTimeout,
    String apiKey
) {
    public void validate() {
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            throw new IllegalStateException("app.payment.gateway-url must be configured");
        }
    }
}
```

## 4. HTTP 调用规范

### RestClient（Spring Boot 4.x 推荐）

```java
// 项目实际使用的模式
var factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(props.timeout());
factory.setReadTimeout(props.timeout());

var builder = RestClient.builder()
        .baseUrl(props.baseUrl())
        .defaultHeader("Content-Type", "application/json")
        .requestFactory(factory)
        .requestInterceptor(new LoggingClientHttpRequestInterceptor());
configureRestClient(builder);  // 子类覆盖以添加认证 header
return builder.build();
```

### 规范

- 使用 `RestClient`（非 `RestTemplate`）
- 必须设置连接超时和读取超时
- 认证 header 通过子类覆盖 `configureRestClient` 添加
- HTTP 状态码通过 `onStatus` 映射到业务异常
- 请求/响应日志通过 `requestInterceptor` 统一处理

```java
// 状态码映射
return restClient.post()
        .uri(getChatEndpoint())
        .body(requestBody)
        .retrieve()
        .onStatus(s -> s.value() == 401 || s.value() == 403,
                (req, resp) -> { throw new LlmAuthException(...); })
        .onStatus(s -> s.value() == 429,
                (req, resp) -> { throw new LlmRateLimitException(...); })
        .onStatus(s -> s.value() >= 500,
                (req, resp) -> { throw new LlmServerException(...); })
        .body(getChatResponseType());
```

## 5. 流式响应

```java
// 使用 Reactor Flux + SSE 逐行解析
return Flux.<ChatResponse>create(sink -> {
    restClient.post()
            .uri(getChatEndpoint())
            .body(requestBody)
            .exchange((req, resp) -> {
                try (var reader = new BufferedReader(
                        new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            var chunk = parseSseLineInternal(line.substring(6).trim());
                            if (chunk != null) { sink.next(chunk); }
                        }
                    }
                }
                sink.complete();
                return null;
            });
})
.doOnError(e -> log.warn("Stream error: {}", e.getMessage()))
.doFinally(signal -> observation.stop())
.subscribeOn(Schedulers.boundedElastic());
```

### 规范

- 流式响应用 `Flux<T>` + SSE 解析
- `doFinally` 中清理资源（Observation stop、连接关闭）
- `doOnError` 中记录错误和观测
- `subscribeOn(Schedulers.boundedElastic())` 避免阻塞调用线程

## 6. 异常处理

```java
// 全局异常处理器
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "内部错误"));
    }
}

// 自定义业务异常
public class BusinessException extends RuntimeException {
    private final String code;
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
    public String getCode() { return code; }
}
```

### 异常设计原则

- 异常有明确业务语义
- 模块级基类提供 `isRetryable()` 判断
- 禁止静默吞异常
- 禁止返回 `null` 掩盖失败
- 公共 API 响应不泄露内部异常、凭据、策略细节

## 7. 统一响应结构

```java
public record ApiResponse<T>(
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime timestamp,
    int code,
    String message,
    T data
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(LocalDateTime.now(), 200, "success", data);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(LocalDateTime.now(), 500, message, null);
    }
}
```

## 8. 配置管理

- 配置、密钥和环境差异必须经配置对象或环境变量显式传入
- 不提交 `.env`、令牌、真实连接地址
- 示例配置只保留占位值
- 使用 `@ConfigurationProperties` 绑定类型安全的配置对象
- Properties 优先使用 `record`，提供 `validate()` 启动时校验

## 9. 观测性（Micrometer Observation）

```java
var ctx = new ChatModelObservationContext(provider, model, request, false);
var observation = Observation.createNotStarted(
        DefaultChatModelObservationConvention.OBSERVATION_NAME,
        () -> ctx, observationRegistry)
        .observationConvention(observationConvention);

return observation.observe(() -> {
    // 业务逻辑
});
```

### 规范

- 每个外部调用创建 Observation
- Convention 放 `observation/` 子包
- `ObservationRegistry` 可为 `NOOP`（当 Micrometer 未配置时）
- 流式调用在 `doFinally` 中 `stop()`，在 `doOnError` 中 `error()`

## 10. 外部调用规范

- 所有外部调用必须设置超时（连接超时 + 读取超时）
- 涉及重试、消息消费和写操作时保证幂等
- 使用 `RestClient` 或 Feign Client
- 保留必要的审计信息（请求追踪 ID、操作者、时间戳）
- 重试通过 `RetryUtils` 统一管理，区分可重试与不可重试异常

## 11. 虚拟线程（按需）

只有项目明确启用虚拟线程且当前 JDK、Spring Boot 版本支持时，才应用本节约束。

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### 注意事项

- 需要降低 HikariCP `maximumPoolSize`：虚拟线程非 1:1 绑定 OS 线程
- 避免在虚拟线程中使用 `synchronized` 块（会 pin 住载体线程）
- 使用 `ReentrantLock` 替代 `synchronized`

## 12. 依赖管理

- 优先使用 Spring Boot 管理的版本（`spring-boot-dependencies`）
- 新增外部依赖前评估：解决的实际问题、许可证、运行影响
- 项目通过在 BOM 中前置 `junit-bom` 锁定 JUnit 版本，避免 Spring Boot 4.1.0 的 JUnit 6.x 与 IntelliJ Runner 不兼容
- 避免重复实现成熟库已有的安全机制
