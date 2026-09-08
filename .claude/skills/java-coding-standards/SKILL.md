---
name: java-coding-standards
description: Java 编码规范：命名、格式化、注释、异常处理、日志 — 融合 Google Java Style Guide、阿里巴巴 Java 开发手册、Oracle JDK 21 Javadoc 规范
user_invocable: true
---

# Java 编码规范

编写或重构 Java 代码时，自动应用以下规范。

## 1. 文件结构

```
package statement

import static ...    // 静态导入组
import ...           // 非静态导入组（按 ASCII 排序，无通配符）

/**
 * 类 Javadoc
 */
public class ClassName {
    // 静态变量
    // 实例变量
    // 构造器
    // 方法（public → protected → private）
    // 重载方法连续排列，不分割
}
```

## 2. 命名规范

| 元素 | 风格 | 示例 |
|------|------|------|
| 包/模块 | 全小写，无下划线 | `com.vcredit.controlplane` |
| 类/接口 | UpperCamelCase，名词短语 | `WorkItemService`, `HttpClient` |
| 方法 | lowerCamelCase，动词短语 | `getUserById()`, `calculateTotal()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PORT` |
| 字段/参数/局部变量 | lowerCamelCase | `userName`, `orderList` |
| 泛型类型变量 | 单大写字母+数字，或类名+T | `E`, `T2`, `RequestT` |
| 测试类 | 以 `Test` 结尾 | `UserServiceTest` |

## 3. 格式化

- **缩进**：2 空格（不使用 Tab）
- **列宽**：120 字符
- **大括号**：K&R 风格（`{` 行尾，`}` 独占一行）
- **条件/循环**：必须使用大括号，即使只有一行
- **import**：无通配符；静态导入和非静态导入分组，之间空一行
- **变量声明**：每行一个变量；局部变量声明靠近首次使用位置
- **数组**：`String[] args`（非 `String args[]`）
- **long**：大写 `L`（非小写 `l`）
- **@Override**：所有覆写方法必须标注

## 4. 注释与 Javadoc

### Javadoc（强制）

- 所有 `public` 类/方法/字段 + 所有抽象方法/接口方法 → 必须写 Javadoc
- 所有类 → 必须包含 `@author` 和 `@since`
- 所有枚举字段 → 必须有注释

### 块标签顺序

```
@param → @return → @throws → @see → @since → @deprecated
```

### 摘要句

- 第一句为名词/动词短语，非完整句子，但以大写开头、句号结尾
- 不写冗余开头（`This method returns...` 等）
- JDK 16+ 推荐用 `{@return description}` 替代 `@return`

### 行内注释

- 注释解释 **为什么**（why），而非 **是什么**（what）
- 单行 `//` 放在语句上方；多行 `/* ... */` 正确缩进
- 与其用"半吊子"英文，不如用中文说清楚

### TODO / FIXME

```java
// TODO(name, 2026-01-01): 描述需要完成的工作
// FIXME(name, 2026-01-01): 描述需要修复的问题
```

### 注释掉的代码

- 直接删除（Git 历史已保存），不提交大段注释代码

## 5. 异常处理

- 不允许静默吞异常；捕获后必须处理或重新抛出
- 不允许 `catch (Exception e) {}` 空块（除非有明确注释说明理由）
- 不允许 `return null` 掩盖失败；使用 `Optional` 或抛异常
- 自定义异常继承 `RuntimeException`，有明确业务语义
- 使用全局异常处理器（`@ControllerAdvice`），不泄露内部异常到 HTTP 响应
- 日志记录异常时必须包含堆栈：`log.error("message", exception)`

## 6. 日志

- 项目已使用日志框架时，优先使用 SLF4J；只有项目已引入 Lombok 时才使用 `@Slf4j`
- 关键操作：`log.info("action={}, userId={}, result={}", ...)`
- 异常：`log.error("message", exception)`（含堆栈）
- 不记录敏感信息（密码、Token、手机号、身份证）
- 日志使用参数化（`{}`），而非字符串拼接

## 7. 通用最佳实践

- 优先构造器注入，不用字段注入（`@Autowired` 字段）
- 不可变对象优先：`final` 字段、`record` 类型
- 避免魔法值：使用常量或枚举
- 避免 `Map<String, Object>` 和万能工具类
- 不提前过度设计：单一实现不创建接口
- 优先组合而非继承
