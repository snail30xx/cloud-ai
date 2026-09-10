package com.cloudai.execution.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具注解 — 标注在方法上，声明该方法为一个可被 LLM 调用的工具。
 *
 * <p>配合 {@link com.cloudai.execution.impl.DefaultToolRegistry#register(Object)} 使用：
 * 注册表会扫描标注了 {@code @Tool} 的方法，自动生成
 * {@link com.cloudai.core.model.ToolDefinition} 和反射执行器。</p>
 *
 * <pre>{@code
 * class MyTools {
 *     @Tool(name = "echo", description = "Echo the input text")
 *     String echo(@ToolParam(description = "Text to echo") String text) {
 *         return text;
 *     }
 * }
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /** 工具名称，默认空串表示使用方法名。 */
    String name() default "";

    /** 工具描述，必填。 */
    String description();
}
