# Cloud AI — Project Context

## 项目概述

Cloud AI 是一个 Java AI Agent 运行时框架（Agent Harness），提供 LLM 适配、记忆、人格、上下文、技能、
工具执行、安全治理和 Agent 编排能力。框架模块为纯 JDK 实现，Spring Boot 集成以可选模块提供。

## 技术栈

| 维度 | 选用 |
|------|------|
| 语言 | Java 21 |
| 构建 | Maven 多模块（12 个子模块） |
| HTTP | JDK `java.net.http.HttpClient`（框架模块）/ JDK `HttpServer`（cloud-ai-server）/ Spring MVC（仅 cloud-ai-spring） |
| 序列化 | Jackson 2.18.2 |
| 响应式 | Reactor (Flux/Mono) |
| 观测 | Micrometer Observation |
| 日志 | SLF4J 2.0.16 / Logback 1.5.12（cloud-ai-spring 内对齐 Boot 4.1 的 1.5.34） |
| Spring | Spring Boot 4.1.0（仅 `cloud-ai-spring`，经 `spring-boot-dependencies` BOM） |
| 测试 | JUnit 5.11.4 + Mockito 5.12.0（`@SpringBootTest` 仅限 cloud-ai-spring） |

## 模块架构

```
cloud-ai-core       核心词汇表（core.chat / core.prompt / core.tool，含 ContextManager）
  ↑
cloud-ai-llm        LLM 适配（adapter / client / advisor / retry / observation / stream）
cloud-ai-memory     记忆（MemoryStore / MemoryRetriever / SimpleContextManager）
cloud-ai-persona    人格（PersonaProvider / PersonaAssembler）
cloud-ai-context    环境上下文（ContextProvider）
cloud-ai-skills     技能（SkillRegistry 渐进加载）
cloud-ai-security   安全治理（permission / approval / audit）
cloud-ai-execution  工具执行（registry / annotation / builtin）
  ↑
cloud-ai-runtime    Agent 循环（loop / lifecycle，Session/Turn/Step 三级 SPI）
  ↑
cloud-ai-server     独立 HTTP 服务 + CloudAi 门面（JDK HttpServer，无 Spring）
cloud-ai-spring     Spring Boot 自动装配壳（可选）
cloud-ai-example    端到端示例
```

## 编码规范

- 包名：`com.cloudai.{module}`，**按领域分包**（接口与默认实现同包共置），
  不使用 `spi/`、`impl/`、`model/` 技术分层目录；`config` 子包为唯一固定技术子包
- 模型类：Java `record` 类型
- SPI 接口：单一职责，`@FunctionalInterface` 优先；默认实现 `Default`/`InMemory` 前缀
- 框架模块装配：`config.XxxAutoConfiguration` 纯静态工厂 + `XxxProperties` record，零 Spring 注解
- Spring 装配（仅 cloud-ai-spring）：`@AutoConfiguration` + `@ConditionalOnProperty` +
  `@ConditionalOnMissingBean`，构造器注入，`@EnableConfigurationProperties` 绑定
- 方法引用前缀 `@Nullable` 使用 JSpecify
- 日志使用 SLF4J；外部调用设置超时；流式响应使用 Reactor `Flux`

## 关键约定

- 依赖方向：core ← llm ← runtime ← server；memory/persona/context/skills/security/execution 为平级模块
  （security 被 execution 依赖；runtime 直接依赖 core/llm/execution）
- 每个模块提供 `config` 静态工厂，通过 `cloud-ai.{module}.enabled` 属性控制（Spring 路径）
- 安全优先：默认拒绝，显式授权；审批模式 auto/manual
- 配置项必须有消费方：新增配置键需同步绑定类与文档（`cloud-ai-server.properties` / README）
- 独立服务器配置文件命名 `cloud-ai-server.properties`（避开 `application.*`，防止 Spring Boot 误加载）
- 测试遵循 BCDE 原则（边界/正确/设计对齐/异常），HTTP 一律 mock 或 Stub
