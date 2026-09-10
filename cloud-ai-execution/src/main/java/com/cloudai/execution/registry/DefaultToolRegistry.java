package com.cloudai.execution.registry;

import com.cloudai.core.tool.ToolCall;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.execution.annotation.Tool;
import com.cloudai.execution.annotation.ToolParam;
import com.cloudai.execution.ToolResult;
import com.cloudai.execution.ToolExecutor;
import com.cloudai.execution.registry.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认工具注册表 — 基于 ConcurrentHashMap 的内存注册表。
 *
 * <p>支持两种注册方式：</p>
 * <ol>
 *   <li>{@link #register(ToolDefinition, ToolExecutor)} — 手动注册定义与执行器</li>
 *   <li>{@link #register(Object)} — 扫描 Bean 上标注 {@link Tool} 的方法自动注册</li>
 * </ol>
 *
 * <p>注解注册模式下的 JSON Schema 构建能力：</p>
 * <ul>
 *   <li>从 Java 类型自动推导 {@code string} / {@code boolean} / {@code integer} / {@code number} / {@code array} / {@code object}</li>
 *   <li>Java 枚举类型自动生成 {@code enum} 可选值</li>
 *   <li>数组类型（{@code List} / {@code Set} / {@code T[]}）生成 {@code items} 子 Schema</li>
 *   <li>{@link ToolParam} 注解的约束字段（{@code minLength} / {@code maxLength} / {@code pattern} /
 *       {@code minimum} / {@code maximum} / {@code format} / {@code enumValues} / {@code defaultValue}）</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultToolRegistry implements ToolRegistry {
    private static final Logger log = LoggerFactory.getLogger(DefaultToolRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();

    @Override
    public @Nullable ToolExecutor find(String toolName) {
        var entry = tools.get(toolName);
        return entry != null ? entry.executor() : null;
    }

    @Override
    public List<ToolDefinition> listDefinitions() {
        var defs = new ArrayList<ToolDefinition>(tools.size());
        tools.forEach((name, entry) -> defs.add(entry.definition()));
        return List.copyOf(defs);
    }

    @Override
    public void register(ToolDefinition definition, ToolExecutor executor) {
        tools.put(definition.name(), new ToolEntry(definition, executor));
        log.info("Tool registered: {}", definition.name());
    }

    /**
     * 扫描 Bean 上标注 {@link Tool} 的方法，自动注册为工具。
     *
     * <p>每个 {@code @Tool} 方法生成一个 {@link ToolDefinition}（参数 JSON Schema
     * 从 {@link ToolParam} 注解和方法签名推导）和一个反射 {@link ToolExecutor}，
     * 后者将 LLM 传入的 JSON 参数映射到方法参数并调用。</p>
     *
     * @param toolBean 包含 {@code @Tool} 方法的 Bean 实例
     */
    public void register(Object toolBean) {
        for (Method method : toolBean.getClass().getDeclaredMethods()) {
            var toolAnno = method.getAnnotation(Tool.class);
            if (toolAnno == null) {
                continue;
            }
            method.setAccessible(true);
            var name = toolAnno.name().isBlank() ? method.getName() : toolAnno.name();
            var params = buildParametersSchema(method);
            var def = new ToolDefinition(name, toolAnno.description(), params);
            register(def, new ReflectiveToolExecutor(toolBean, method));
        }
    }

    /** 已注册工具数量。 */
    public int size() {
        return tools.size();
    }

    private record ToolEntry(ToolDefinition definition, ToolExecutor executor) {}

    // ---- JSON Schema 构建 ----

    /**
     * 根据方法参数上的 {@link ToolParam} 注解和 Java 类型构建 JSON Schema。
     *
     * @param method 标注了 {@link Tool} 的方法
     * @return JSON Schema Map，无参数时返回空 Map
     */
    private Map<String, Object> buildParametersSchema(Method method) {
        var params = method.getParameters();
        if (params.length == 0) {
            return Map.of();
        }

        var properties = new LinkedHashMap<String, Object>();
        var required = new ArrayList<String>();

        for (Parameter param : params) {
            var anno = param.getAnnotation(ToolParam.class);
            var paramName = (anno != null && !anno.name().isBlank()) ? anno.name() : param.getName();
            properties.put(paramName, buildPropertySchema(param.getType(), anno));
            if (anno == null || anno.required()) {
                required.add(paramName);
            }
        }

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.copyOf(required));
    }

    /**
     * 为单个参数构建完整 JSON Schema Map，包括类型推导、数组 items、枚举值和约束字段。
     *
     * @param type 参数的 Java 类型
     * @param anno 参数上的 {@link ToolParam} 注解，可能为 null
     * @return JSON Schema Map
     */
    private Map<String, Object> buildPropertySchema(Class<?> type, @Nullable ToolParam anno) {
        var schema = new LinkedHashMap<String, Object>();

        // 数组类型优先处理
        if (isArrayType(type)) {
            schema.put("type", "array");
            schema.put("items", buildArrayItemsSchema(type, anno));
        } else if (type.isEnum()) {
            schema.put("type", "string");
            schema.put("enum", enumConstantNames(type));
        } else {
            schema.put("type", jsonSchemaType(type));
        }

        // 注解约束
        if (anno != null) {
            applyAnnotationConstraints(schema, anno, type);
        }

        return schema;
    }

    /** 构建数组元素的 JSON Schema。 */
    private Map<String, Object> buildArrayItemsSchema(Class<?> arrayType, @Nullable ToolParam anno) {
        var itemType = (anno != null && anno.itemType() != Object.class) ? anno.itemType() : String.class;

        var items = new LinkedHashMap<String, Object>();
        if (itemType.isEnum()) {
            items.put("type", "string");
            items.put("enum", enumConstantNames(itemType));
        } else {
            items.put("type", jsonSchemaType(itemType));
        }
        return items;
    }

    /** 将 Java 类型映射为 JSON Schema 类型字符串。 */
    private static String jsonSchemaType(Class<?> type) {
        if (type == String.class || type == char.class || type == Character.class
                || CharSequence.class.isAssignableFrom(type)) {
            return "string";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == double.class || type == float.class
                || type == Double.class || type == Float.class
                || type == BigDecimal.class) {
            return "number";
        }
        if (type.isPrimitive() || type == Integer.class || type == Long.class
                || type == Short.class || type == Byte.class
                || type == BigInteger.class) {
            return "integer";
        }
        return "object";
    }

    /** 判断是否为数组类型（数组、List、Set）。 */
    private static boolean isArrayType(Class<?> type) {
        return type.isArray()
                || List.class.isAssignableFrom(type)
                || Set.class.isAssignableFrom(type);
    }

    /** 获取枚举类型的常量名称列表。 */
    private static List<String> enumConstantNames(Class<?> enumType) {
        var constants = enumType.getEnumConstants();
        var names = new ArrayList<String>(constants.length);
        for (var c : constants) {
            names.add(((Enum<?>) c).name());
        }
        return List.copyOf(names);
    }

    /**
     * 将 {@link ToolParam} 注解的约束字段应用到 Schema Map。
     *
     * @param schema 待填充的 Schema Map
     * @param anno   注解实例
     * @param type   参数 Java 类型，用于判断约束是否适用
     */
    private void applyAnnotationConstraints(Map<String, Object> schema, ToolParam anno, Class<?> type) {
        // description
        if (!anno.description().isBlank()) {
            schema.put("description", anno.description());
        }
        // defaultValue
        if (!anno.defaultValue().isBlank()) {
            schema.put("default", anno.defaultValue());
        }
        // enumValues — 显式覆盖自动推导的枚举值
        if (anno.enumValues().length > 0) {
            schema.put("enum", List.of(anno.enumValues()));
        }
        // format
        if (!anno.format().isBlank()) {
            schema.put("format", anno.format());
        }
        // 字符串约束
        var schemaType = (String) schema.get("type");
        if ("string".equals(schemaType)) {
            if (anno.minLength() >= 0) {
                schema.put("minLength", anno.minLength());
            }
            if (anno.maxLength() >= 0) {
                schema.put("maxLength", anno.maxLength());
            }
            if (!anno.pattern().isBlank()) {
                schema.put("pattern", anno.pattern());
            }
        }
        // 数值约束（integer / number）
        if ("integer".equals(schemaType) || "number".equals(schemaType)) {
            if (!Double.isNaN(anno.minimum())) {
                schema.put("minimum", anno.minimum());
            }
            if (!Double.isNaN(anno.maximum())) {
                schema.put("maximum", anno.maximum());
            }
        }
    }

    // ---- 反射执行器 ----

    /**
     * 反射工具执行器 — 解析 JSON 参数，调用目标方法，序列化返回值。
     *
     * @author cloud-ai
     * @since 1.0
     */
    private static final class ReflectiveToolExecutor implements ToolExecutor {
        private final Object bean;
        private final Method method;

        ReflectiveToolExecutor(Object bean, Method method) {
            this.bean = bean;
            this.method = method;
        }

        @Override
        public ToolResult execute(ToolCall toolCall) {
            try {
                var args = parseArguments(toolCall.arguments());
                var result = method.invoke(bean, args);
                return ToolResult.success(toolCall.id(), serializeResult(result));
            } catch (InvocationTargetException e) {
                var cause = e.getCause() != null ? e.getCause() : e;
                return ToolResult.failure(toolCall.id(), cause.getMessage());
            } catch (Exception e) {
                return ToolResult.failure(toolCall.id(), e.getMessage());
            }
        }

        private Object[] parseArguments(String json) throws Exception {
            var params = method.getParameters();
            if (params.length == 0) {
                return new Object[0];
            }

            JsonNode root = (json == null || json.isBlank())
                    ? MAPPER.nullNode()
                    : MAPPER.readTree(json);

            var args = new Object[params.length];
            for (int i = 0; i < params.length; i++) {
                var param = params[i];
                var anno = param.getAnnotation(ToolParam.class);
                var paramName = (anno != null && !anno.name().isBlank()) ? anno.name() : param.getName();
                var node = root.get(paramName);

                if (node == null || node.isNull()) {
                    args[i] = param.getType().isPrimitive() ? defaultPrimitive(param.getType()) : null;
                } else {
                    args[i] = MAPPER.convertValue(node, param.getType());
                }
            }
            return args;
        }

        private static Object defaultPrimitive(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            return 0;
        }

        private String serializeResult(Object result) throws Exception {
            if (result == null) {
                return "null";
            }
            if (result instanceof String s) {
                return s;
            }
            return MAPPER.writeValueAsString(result);
        }
    }
}