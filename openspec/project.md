# Cloud AI — Project Context

## 项目概述

Cloud AI 是一个 Java AI Agent 运行时框架，提供 LLM 适配、工具调用、安全治理和 Agent 编排能力。

## 技术栈

| 维度 | 选用 |
|------|------|
| 语言 | Java 21 |
| 构建 | Maven 多模块 |
| 框架 | Spring Boot 4.1.0 |
| 测试 | JUnit 5.11.4 + Mockito 5.12.0 |
| 观测 | Micrometer (Observation) |
| 响应式 | Reactor (Flux/Mono) |
| 包管理 | Maven BOM (spring-boot-dependencies) |

## 模块架构

```
cloud-ai-core       ← 核心模型和 SPI 接口（ChatModel, ModelDiscovery）
  ↑
cloud-ai-llm        ← LLM 适配层（OpenAI, DeepSeek, Anthropic）
  ↑
cloud-ai-security   ← 安全治理（PermissionManager, ApprovalGateway, AuditLogger）
  ↑
cloud-ai-execution  ← 工具执行（FileSystem, Shell, Sandbox）
  ↑
cloud-ai-runtime    ← Agent 运行时（AgentLoop, ToolRegistry）
  ↑
cloud-ai-server     ← Spring Boot 启动模块
```

## 编码规范

- 包名：`com.cloudai.{module}.{spi|model|impl|config}`
- 模型类：Java `record` 类型
- SPI 接口：单一职责，`@FunctionalInterface` 优先
- 自动装配：`@Configuration` + `@ConditionalOnProperty` + `@ConditionalOnMissingBean`
- 构造器注入，无 Lombok
- 方法引用前缀 `@Nullable` 使用 JSpecify
- 日志使用 SLF4J

## 关键约定

- SPI 接口定义在自身模块中（不放 core），除非所有上层模块都依赖它
- 默认实现放在 `impl/` 包下，通过 `@ConditionalOnMissingBean` 允许替换
- 每个模块提供 `AutoConfiguration` 类，通过 `cloud-ai.{module}.enabled` 属性控制
- 安全优先：默认拒绝，显式授权