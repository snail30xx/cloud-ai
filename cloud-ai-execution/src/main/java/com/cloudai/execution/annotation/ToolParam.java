package com.cloudai.execution.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解 — 标注在 @Tool 方法的参数上，描述参数元信息与 JSON Schema 约束。
 *
 * <p>未标注此注解的参数仍可被注册表识别（依赖 -parameters 编译选项
 * 获取参数名，并根据 Java 类型推断 JSON Schema 类型），但缺少 description 和约束。</p>
 *
 * <h3>支持的 JSON Schema 扩展</h3>
 * <ul>
 *   <li>{@link #defaultValue} — JSON Schema {@code default}</li>
 *   <li>{@link #enumValues} — JSON Schema {@code enum}</li>
 *   <li>{@link #minLength} / {@link #maxLength} — 字符串长度约束</li>
 *   <li>{@link #pattern} — 正则约束</li>
 *   <li>{@link #minimum} / {@link #maximum} — 数值范围约束</li>
 *   <li>{@link #format} — 格式标记（如 date-time、uri、email）</li>
 *   <li>{@link #itemType} — 数组元素类型提示（泛型擦除后无法自动推断）</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /** 参数名，默认空串表示使用实际参数名。 */
    String name() default "";

    /** 参数描述。 */
    String description() default "";

    /** 是否必填，默认 true。 */
    boolean required() default true;

    /** 默认值（字符串形式），空串表示不设置。 */
    String defaultValue() default "";

    /** 枚举可选值，空数组表示不设置。 */
    String[] enumValues() default {};

    /** 字符串最小长度，-1 表示不设置。 */
    int minLength() default -1;

    /** 字符串最大长度，-1 表示不设置。 */
    int maxLength() default -1;

    /** 正则约束，空串表示不设置。 */
    String pattern() default "";

    /** 数值下界（含），NaN 表示不设置。 */
    double minimum() default Double.NaN;

    /** 数值上界（含），NaN 表示不设置。 */
    double maximum() default Double.NaN;

    /** 格式标记，如 date-time、uri、email，空串表示不设置。 */
    String format() default "";

    /** 数组元素类型提示（泛型擦除后无法自动推断），默认 Object.class 表示不设置。 */
    Class<?> itemType() default Object.class;
}