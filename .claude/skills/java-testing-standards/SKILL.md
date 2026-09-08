---
name: java-testing-standards
description: JUnit 5 测试规范：BCDE 原则、参数化测试、Mock 策略、反模式识别 — 基于 JUnit 5 最佳实践、阿里巴巴 BCDE 原则、Martin Fowler 测试价值论
user_invocable: true
---

# Java 测试规范

编写或修改测试代码时，自动应用以下规范。

## 1. BCDE 测试原则

只测试有*行为价值*的代码，跳过框架和基础设施。

| 类别 | 说明 | 示例 |
|------|------|------|
| **B**order | 边界值、空集合、null、max/min | `parseResponse(null)` → 返回空不是 NPE |
| **C**orrect | 正确路径，有效输入产生预期输出 | `parseResponse(validJson)` → 正确 ChatResponse |
| **D**esign | 与文档和需求对齐的行为 | `buildRequestBody()` 包含 `extraBody` 合并 |
| **E**rror | 异常路径、错误处理 | `validate()` 抛出 `IllegalStateException` |

**禁止测试：**
- getter/setter、无逻辑构造器、POJO
- Spring 配置绑定、Jackson 序列化（框架职责）
- 日志输出、`System.out`
- 私有方法、内部实现细节

## 2. 测试结构

```
src/test/java/com/cloudai/{module}/
├── FooTest.java              ← 一个产品类对应一个测试类
│   ├── @Nested class CorrectPathTests { }
│   ├── @Nested class BorderCaseTests { }
│   └── @Nested class ErrorPathTests { }
```

**AAA 模式（用空行分隔）：**
```java
@Test
@DisplayName("should return empty response when input is null")
void shouldReturnEmptyResponseWhenInputIsNull() {
    // Arrange
    var adapter = new OpenAiLlmAdapter(props);

    // Act
    var result = adapter.parseResponseForTest(null);

    // Assert
    assertThat(result.content()).isEmpty();
    assertThat(result.usage().totalTokens()).isZero();
}
```

## 3. 参数化测试

**同类多 case 合并为一个 `@ParameterizedTest`，禁止每个 case 一个 `@Test`：**

```java
// ❌ 错误：6 个 @Test 测试同一个 switch
@Test void shouldMapStop() { ... }
@Test void shouldMapLength() { ... }
@Test void shouldMapToolCalls() { ... }

// ✅ 正确：合并为 ParameterizedTest
@ParameterizedTest
@CsvSource({
    "stop,          STOP",
    "length,        LENGTH",
    "tool_calls,    TOOL_CALLS",
    "content_filter, CONTENT_FILTER",
    ",              STOP",
    "unknown_reason, UNKNOWN"
})
@DisplayName("should map finish reason correctly")
void shouldMapFinishReason(String input, FinishReason expected) {
    assertThat(adapter.parseFinishReasonForTest(input)).isEqualTo(expected);
}
```

```java
// ✅ 多个异常类型共用同一个测试逻辑
@ParameterizedTest
@MethodSource("retryableExceptions")
@DisplayName("should correctly report retryable status")
void shouldReportRetryable(LlmException ex, boolean expected) {
    assertThat(ex.isRetryable()).isEqualTo(expected);
}

static Stream<Arguments> retryableExceptions() {
    return Stream.of(
        Arguments.of(new LlmServerException("x", 500, "err"), true),
        Arguments.of(new LlmRateLimitException("x", "err"), true),
        Arguments.of(new LlmTimeoutException("x", "err", null), true),
        Arguments.of(new LlmAuthException("x", 401, "err"), false),
        Arguments.of(new LlmClientException("x", 400, "err"), false)
    );
}
```

## 4. Mock 策略

| 层 | Mock 策略 |
|----|-----------|
| `*Adapter` 单元测试 | Mock RestClient，不真实发包 |
| `ModelRouter` 单元测试 | Mock ModelAdapter，不依赖 Adapter 实现 |
| `ChatClient` 单元测试 | Mock ModelRouterAccessor |
| 配置校验 | 纯 JUnit 5，不启动 Spring 上下文 |
| 集成测试 | 用 `@SpringBootTest` + Testcontainers，放在 `*IT.java` |

```java
// ❌ 错误：真实 HTTP 调用（慢、不稳定、依赖外部）
@Test
void shouldFetchModelInfo() {
    var adapter = new OpenAiLlmAdapter(realProps);
    var info = adapter.getModelInfo(); // 真实 HTTP 调用
}

// ✅ 正确：Mock RestClient
@Test
void shouldFallbackToConfigWhenApiFails() {
    var mockClient = mock(RestClient.class);
    // ... 设置 mock 返回 401
    var adapter = new OpenAiLlmAdapter(props, mockClient);
    var info = adapter.getModelInfo();
    assertThat(info.model()).isEqualTo("gpt-4o"); // 回退到配置值
}
```

## 5. 断言规范

```java
// ✅ 使用 AssertJ 流式断言
assertThat(result.content()).isEqualTo("Hello");
assertThat(result.toolCalls()).hasSize(2);
assertThatThrownBy(() -> props.validate())
    .isInstanceOf(IllegalStateException.class)
    .hasMessageContaining("https://");

// ✅ 使用 assertThrows 替代 try-catch + fail()
var ex = assertThrows(IllegalStateException.class, router::validate);
assertThat(ex.getMessage()).contains("not registered");

// ❌ 禁止
try {
    router.validate();
    fail("should have thrown");
} catch (IllegalStateException e) {
    // pass
}
```

## 6. 测试审核清单

每写完一个测试，自问：
1. 这个测试失败了说明什么？—— 如果答案是"框架坏了"→ 删掉
2. 删掉这个测试，产品代码的正确性能被其他测试覆盖吗？—— 如果覆盖 → 删掉
3. 这个测试有 Arrange / Act / Assert 三个明确段落吗？—— 如果没有 → 重构
4. 跑一次需要多久？—— 如果 >100ms → 检查是否 mock 了外部调用

## 7. 快速命令

```bash
# 只跑单元测试（快）
mvn test -pl cloud-ai-core,cloud-ai-llm

# 全量构建（含集成测试）
mvn verify
```