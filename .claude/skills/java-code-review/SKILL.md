---
name: java-code-review
description: Java 代码审查清单：安全检查、性能优化、可维护性、异常处理、并发安全、常见陷阱 — 用于代码审查和提交前检查
user_invocable: true
---

# Java 代码审查清单

审查 Java 代码时，按以下维度逐项检查；只检查项目实际使用的技术栈，不对不存在的数据库、Web 层或消息系统做假设。

## 1. 安全检查 🔒

- [ ] **SQL 注入**：MyBatis 使用 `#{}` 而非 `${}`；动态排序/表名必须白名单校验
- [ ] **XSS**：所有用户输入输出到前端时做 HTML 转义
- [ ] **SSRF**：存在可控 URL 或外部请求时，校验协议、目标和重定向，防止内网请求伪造
- [ ] **敏感信息**：日志不记录密码、Token、手机号、身份证
- [ ] **权限校验**：每个 Controller 方法必须有权限检查（`@PreAuthorize` 或自定义注解）
- [ ] **数据脱敏**：API 响应中敏感字段脱敏
- [ ] **文件上传**：限制文件类型、大小；不直接使用用户提供的文件名
- [ ] **反序列化**：不使用 Java 原生序列化接收外部数据；优先 JSON

## 2. 异常处理 ⚠️

- [ ] 不允许静默吞异常：`catch (Exception e) {}` 必须有注释说明理由
- [ ] 不允许 `e.printStackTrace()` 替代日志
- [ ] 不允许 `return null` 掩盖失败；使用 `Optional` 或抛异常
- [ ] 异常有明确业务语义，不滥用 `RuntimeException`
- [ ] 全局异常处理器已覆盖所有自定义异常类型
- [ ] `finally` 块中不抛出异常（会覆盖原始异常）
- [ ] 资源关闭使用 try-with-resources

## 3. 并发安全 🔀

- [ ] 共享可变状态使用 `synchronized`、`ReentrantLock` 或 `Atomic*` 类
- [ ] 优先使用 `ConcurrentHashMap` 替代手动同步的 `HashMap`
- [ ] 使用 `volatile` 保证变量可见性
- [ ] 线程池使用 `ThreadPoolExecutor` 显式配置（不用 `Executors` 快捷方法）
- [ ] 虚拟线程中避免 `synchronized`（pin 载体线程），使用 `ReentrantLock`
- [ ] `SimpleDateFormat` 不是线程安全的；使用 `DateTimeFormatter`
- [ ] 集合类（`ArrayList`、`HashMap`）不是线程安全的；多线程访问需同步

## 4. 性能优化 ⚡

- [ ] 循环内不执行数据库查询（N+1 问题）
- [ ] 循环内不拼接字符串；使用 `StringBuilder` 或 `String.format()`
- [ ] 大对象不创建在循环中；考虑对象池或缓存
- [ ] 数据库查询使用索引；避免全表扫描和 `SELECT *`
- [ ] 外部调用设置合理超时（连接超时 + 读取超时）
- [ ] 大数据量分页查询使用游标分页（非 offset 分页）
- [ ] 高频调用考虑缓存（本地缓存 Caffeine 或 Redis）
- [ ] 日志参数化（`{}`），非字符串拼接

## 5. 可维护性 📖

- [ ] 类和方法单一职责，不超过合理行数（类 < 500 行，方法 < 50 行）
- [ ] 命名清晰表达业务含义，无缩写
- [ ] 魔法值替换为常量或枚举
- [ ] 复杂条件判断提取为有意义的方法名
- [ ] 嵌套层级不超过 3 层（Guard Clause 提前返回）
- [ ] 不使用通配符导入
- [ ] 删除无用代码（注释掉的代码、未使用的变量/方法/导入）
- [ ] 避免过度泛型（`BaseDao<T, ID, Q, R>` 等）

## 6. 空指针防范 🚫

- [ ] 方法返回空集合用 `Collections.emptyList()` 或 `List.of()` 替代 `null`
- [ ] 使用 `Optional` 表达可能缺失的返回值
- [ ] 确定不能为空的字段使用 `@NonNull` 或 `Objects.requireNonNull()`
- [ ] 优先使用 `record`（不可变类，天然防 null 构造）
- [ ] 字符串比较 `"constant".equals(variable)` 而非 `variable.equals("constant")`
- [ ] 自动拆箱前检查 `null`（`Integer` → `int`）

## 7. 测试相关 ✅

- [ ] 新增/修改行为有对应的自动化测试
- [ ] 覆盖成功路径、失败路径、边界条件
- [ ] 测试命名清晰表达场景（`shouldXxx_whenXxx`）
- [ ] Mock 只用于外部依赖，不 mock 被测试对象本身
- [ ] 测试之间独立，不依赖执行顺序

## 8. 常见陷阱

- ❌ `BigDecimal` 使用 `new BigDecimal(0.1)` → ✅ `new BigDecimal("0.1")`
- ❌ 集合遍历时删除元素 → ✅ 使用 `Iterator.remove()` 或 `removeIf()`
- ❌ `equals` 实现不重写 `hashCode` → ✅ 同时重写（或使用 `record`）
- ❌ 在 `finally` 中 `return` → ✅ 只在 `try` 或 `catch` 中 `return`
- ❌ `switch` 缺少 `default` → ✅ 每个 `switch` 必须包含 `default`
- ❌ 使用 `==` 比较包装类型 → ✅ 使用 `equals()` 或拆箱后比较
- ❌ `long` 后缀用小写 `l` → ✅ 使用大写 `L`
