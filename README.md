# Cloud AI

基于 Spring Boot 的 LLM 适配框架，提供 OpenAI 兼容协议的统一的 Chat API、流式调用、重试、观测与模型路由。

## 模块

| 模块 | 说明 |
|------|------|
| `cloud-ai-core` | 核心模型与 SPI 接口 (`ChatModel`, `ModelDiscovery`) |
| `cloud-ai-llm` | LLM 适配层 (OpenAI, DeepSeek), 重试, 观测, ChatClient |
| `cloud-ai-security` | 安全层 (预留) |
| `cloud-ai-execution` | 执行层 (预留) |
| `cloud-ai-runtime` | 运行时 (预留) |
| `cloud-ai-server` | Spring Boot 应用入口 |

## 快速开始

### 配置

```yaml
cloud-ai:
  llm:
    default-provider: openai
    providers:
      openai:
        base-url: https://api.openai.com/v1
        api-key: ${OPENAI_API_KEY}
        model: gpt-4o
        max-retries: 3
      deepseek:
        base-url: https://api.deepseek.com
        api-key: ${DEEPSEEK_API_KEY}
        model: deepseek-v4-pro
        capabilities: [thinking]
```

### 使用

```java
// 创建 ChatClient
ChatClient client = ChatClientFactory.create(router);

// 同步调用
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

### 自定义观测

```java
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

## 许可

Apache 2.0