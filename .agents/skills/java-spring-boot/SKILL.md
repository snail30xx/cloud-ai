---
name: java-spring-boot
description: Spring Boot 最佳实践：分层架构、构造器注入、异常处理、配置管理和外部调用；版本以项目 POM 为准
user_invocable: true
---

# Spring Boot 最佳实践

仅在项目的 `pom.xml` 实际引入 Spring Boot 时应用以下实践；具体 API、默认行为和可用插件以项目声明的版本为准。

## 1. 分层架构（DDD 风格）

```
controller/     ← REST 接口层：协议转换、参数校验、响应封装
  ↓
service/        ← 业务逻辑层：业务编排、事务管理
  ├── impl/     ← Service 实现类（以 Impl 结尾）
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

// ✅ 推荐：构造器注入（Java 17+ record 风格）
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

## 3. 虚拟线程（按需）

只有项目明确启用虚拟线程且当前 JDK、Spring Boot 版本支持时，才应用本节约束。

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

### ⚠️ 虚拟线程注意事项

- 需要降低 HikariCP `maximumPoolSize`：虚拟线程非 1:1 绑定 OS 线程，传统连接池大小公式不再适用
- 避免在虚拟线程中使用 `synchronized` 块（会 pin 住载体线程）
- 使用 `ReentrantLock` 替代 `synchronized`

## 4. 异常处理

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
- 禁止静默吞异常
- 禁止返回 `null` 掩盖失败
- 公共 API 响应不泄露内部异常、凭据、策略细节

## 5. 统一响应结构

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

## 6. 配置管理

- 配置、密钥和环境差异必须经配置对象或环境变量显式传入
- 不提交 `.env`、令牌、真实连接地址
- 示例配置只保留占位值
- 使用 `@ConfigurationProperties` 绑定类型安全的配置对象

```java
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
    String gatewayUrl,
    int connectTimeout,
    int readTimeout,
    String apiKey
) {}
```

## 7. 外部调用规范

- 所有外部调用必须设置超时（连接超时 + 读取超时）
- 涉及重试、消息消费和写操作时保证幂等
- 使用 Feign Client 或 RestClient（Spring Boot 3.2+）
- 保留必要的审计信息（请求追踪 ID、操作者、时间戳）

## 8. AOT 编译与 Native Image（按需）

- 只有项目有明确的 Native Image 目标时才配置 AOT 编译。
- 通过项目实际使用的 `spring-boot-maven-plugin` 版本配置 AOT；先验证反射、代理和资源注册需求。
- 不对启动时间作固定承诺，以实测结果为准。

## 9. 数据库与事务

- 使用 Flyway 或 Liquibase 管理数据库迁移
- 同一事务内维护业务状态和 Outbox（事件溯源）
- 消费者、重试作业和写操作必须以稳定幂等键去重
- 禁止跨服务直接访问数据库，只通过 API 或事件协作

## 10. 依赖管理

- 优先使用 Spring Boot 管理的版本（`spring-boot-dependencies`）
- 新增外部依赖前评估：解决的实际问题、许可证、运行影响
- 避免重复实现成熟库已有的安全机制
