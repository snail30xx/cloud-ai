# 项目开发约定

## 项目事实

- 当前仓库是 Maven 单模块 Java 项目：`com.vcredit:cloud-ai`。
- 源码位于 `src/main/java`，测试位于 `src/test/java`；当前没有 Spring Boot、数据库、HTTP API 或多模块结构。
- `pom.xml` 是 Java 版本、依赖、插件和测试框架的唯一事实来源。不要把计划中的技术栈写成已实现能力。
- 当前测试依赖是 JUnit 3.8.1。升级测试框架或引入新框架时，必须同时修改 POM、测试和相关文档。

## 开始工作前

- 先执行 `git status --short`，保留并避开已有的无关改动。
- 阅读 `pom.xml`、相关源码和测试；仓库没有 README 或 docs 时，不要假设不存在的模块、接口或部署方式。
- 先复用现有代码和约定，以最小改动解决一个明确问题；需求、数据边界或安全边界不清楚时先澄清。
- 修改 POM、公共接口或构建配置时，检查对现有命令和调用方的影响。

## Java 代码

- 包名使用全小写，遵循现有根包 `com.vcredit`；类、方法、字段和常量分别使用 Java 惯用命名。
- 遵循现有代码格式；不使用通配符 import，覆写方法标注 `@Override`，条件和循环即使只有一条语句也使用大括号。
- 优先使用清晰、不可变的数据类型和构造器；不要为单一实现预先创建接口、抽象层或万能工具类。
- 注释说明原因和约束，不重复代码本身；公共 API、复杂算法和非显然行为应补充简洁 Javadoc。
- 异常必须被处理或继续抛出，不静默吞异常、不用 `null` 掩盖失败、不输出敏感信息。
- 只有在项目实际引入 Spring、数据库、消息或外部 API 后，才应用对应的分层、事务、超时、幂等和重试约束。

## 测试、构建与验证

- 行为变更必须增加或更新最接近该行为的测试，至少覆盖成功路径和关键失败、边界路径。
- 常用命令：`mvn test -pl cloud-ai-core,cloud-ai-llm`；全量构建 `mvn test`。只报告实际执行过的命令和结果。
- 当前 POM 未声明 Java 编译级别；若命令因本机 JDK、依赖或 POM 配置失败，必须说明原因，不得宣称验证通过。
- 不为不存在的模块运行 Node.js、Python、Spring 或数据库检查；新增技术栈时，先补充可重复执行的构建和测试命令。

### 测试规范

**必须测试（BCDE 原则）：**
- **B**order：边界值、空集合、null、max/min
- **C**orrect：正确路径，有效输入产生预期输出
- **D**esign：与文档和需求对齐的行为
- **E**rror：异常路径、错误处理

**不测试：**
- getter/setter/POJO、无逻辑的构造器
- 框架行为（Spring 配置绑定、Jackson 序列化、依赖注入）
- `System.out` / 日志输出
- 私有的内部实现细节

**写法要求：**
- 同类多 case 用 `@ParameterizedTest` + `@CsvSource`/`@MethodSource` 合并，禁止每个 case 一个 `@Test`
- 涉及 HTTP 调用必须 mock，不得真实发包
- 非必要不启动 Spring 上下文（`@SpringBootTest`）；纯逻辑测试用纯 JUnit 5
- 使用 `assertThrows` 替代 `try-catch + fail()`
- 一个测试只验证一个行为概念，按 AAA 模式（Arrange / Act / Assert）分行
- 用 `@Nested` + `@DisplayName` 组织测试层级

**审核标准：** 问自己"这个测试失败了说明什么？"——如果只说明框架坏了，删掉；如果说明业务规则被违反，保留。

## 安全与依赖

- 不提交密钥、令牌、真实连接地址、客户数据或包含敏感字段的日志；示例配置只使用占位值。
- 新增依赖前说明它解决的问题、版本来源和运行影响，优先复用已有依赖。
- 处理外部输入或外部调用时，进行必要的校验并设置超时；写操作、重试或异步消费需要稳定幂等键。

## Git 与交付

- 不使用 `git reset --hard`、`git checkout --` 或未经明确授权的强制推送来覆盖改动。
- 提交和评审前检查 `git diff --check`、`git status --short` 和实际差异，确认没有生成文件、凭据或无关格式化。
- 提交信息使用 Conventional Commits，例如 `fix(app): ...`、`test(app): ...`、`docs: ...`。
- 交付说明应包含行为变化、兼容性或安全影响、实际验证命令及未覆盖项。

## 本地 Skills

`.agents/skills/` 中的 Skill 按需使用，不代表当前项目已经采用对应技术栈：

| Skill | 使用时机 |
|---|---|
| `java-coding-standards` | 编写或重构 Java 代码时 |
| `java-code-review` | 代码审查或提交前检查时 |
| `java-design-patterns` | 确有变化点、外部边界或重复规则，需要设计抽象时 |
| `java-spring-boot` | 仅在 POM 实际引入 Spring Boot 后使用 |
| `java-testing-standards` | 编写测试代码或审查测试质量时 |

当前不需要新增 Skill；若项目引入新的框架、构建工具或部署流程，再新增针对该技术栈的最小 Skill，并同步更新本文件。
