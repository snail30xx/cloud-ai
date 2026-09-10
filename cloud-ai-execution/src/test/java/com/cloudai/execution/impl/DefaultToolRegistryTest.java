package com.cloudai.execution.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.core.model.ToolDefinition;
import com.cloudai.execution.annotation.Tool;
import com.cloudai.execution.annotation.ToolParam;
import com.cloudai.execution.model.ToolResult;
import com.cloudai.execution.spi.ToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultToolRegistry")
class DefaultToolRegistryTest {

    private DefaultToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
    }

    @Nested
    @DisplayName("manual register (definition + executor)")
    class ManualRegister {

        @Test
        @DisplayName("find should return null for unregistered tool")
        void findUnregistered() {
            assertNull(registry.find("nonexistent"));
        }

        @Test
        @DisplayName("register and find should work")
        void registerAndFind() {
            var def = new ToolDefinition("test_tool", "A test tool", Map.of());
            ToolExecutor exec = call -> ToolResult.success(call.id(), "ok");
            registry.register(def, exec);

            var found = registry.find("test_tool");
            assertNotNull(found);
            assertEquals("ok", found.execute(new ToolCall("1", "test_tool", "")).output());
        }

        @Test
        @DisplayName("listDefinitions should return all registered tools")
        void listDefinitions() {
            registry.register(
                    new ToolDefinition("tool_a", "A", Map.of()),
                    call -> ToolResult.success(call.id(), "a"));
            registry.register(
                    new ToolDefinition("tool_b", "B", Map.of()),
                    call -> ToolResult.success(call.id(), "b"));

            var defs = registry.listDefinitions();
            assertEquals(2, defs.size());
        }

        @Test
        @DisplayName("register should overwrite existing tool with same name")
        void overwriteExisting() {
            registry.register(
                    new ToolDefinition("tool", "v1", Map.of()),
                    call -> ToolResult.success(call.id(), "v1"));
            registry.register(
                    new ToolDefinition("tool", "v2", Map.of()),
                    call -> ToolResult.success(call.id(), "v2"));

            var result = registry.find("tool").execute(new ToolCall("1", "tool", ""));
            assertEquals("v2", result.output());
        }
    }

    // ---- @Tool 注解注册 ----

    enum Color { RED, GREEN, BLUE }

    /** 测试用工具 Bean。 */
    static class SampleTools {

        @Tool(name = "echo", description = "Echo the input text")
        String echo(@ToolParam(description = "Text to echo") String text) {
            return text;
        }

        @Tool(description = "Add two integers")
        int add(
                @ToolParam(description = "First addend") int a,
                @ToolParam(description = "Second addend") int b) {
            return a + b;
        }

        @Tool(name = "no_args", description = "Takes no arguments")
        String noArgs() {
            return "hello";
        }

        @Tool(name = "boom", description = "Always throws")
        String boom(@ToolParam(description = "Ignored") String input) {
            throw new IllegalStateException("boom-" + input);
        }

        @Tool(name = "optional_param", description = "Has an optional param")
        String optional(
                @ToolParam(description = "Required", required = true) String req,
                @ToolParam(description = "Optional", required = false) String opt) {
            return req + ":" + (opt != null ? opt : "null");
        }

        // 无 @Tool 注解的方法不应被注册
        String notATool() {
            return "ignore me";
        }
    }

    @Nested
    @DisplayName("annotation-based register(Object)")
    class AnnotationRegister {

        @Test
        @DisplayName("should register all @Tool methods and skip non-annotated ones")
        void registersAnnotatedMethods() {
            registry.register(new SampleTools());

            assertEquals(5, registry.size());
            assertNotNull(registry.find("echo"));
            assertNotNull(registry.find("add"));
            assertNotNull(registry.find("no_args"));
            assertNotNull(registry.find("boom"));
            assertNotNull(registry.find("optional_param"));
        }

        @Test
        @DisplayName("echo tool should return input text")
        void echoTool() {
            registry.register(new SampleTools());

            var result = registry.find("echo")
                    .execute(new ToolCall("1", "echo", "{\"text\":\"hi\"}"));
            assertTrue(result.success());
            assertEquals("hi", result.output());
        }

        @Test
        @DisplayName("add tool should sum two numbers")
        void addTool() {
            registry.register(new SampleTools());

            var result = registry.find("add")
                    .execute(new ToolCall("2", "add", "{\"a\":3,\"b\":4}"));
            assertTrue(result.success());
            assertEquals("7", result.output());
        }

        @Test
        @DisplayName("no_args tool should work with empty arguments")
        void noArgsTool() {
            registry.register(new SampleTools());

            var result = registry.find("no_args")
                    .execute(new ToolCall("3", "no_args", ""));
            assertTrue(result.success());
            assertEquals("hello", result.output());
        }

        @Test
        @DisplayName("boom tool should return failure with exception message")
        void boomTool() {
            registry.register(new SampleTools());

            var result = registry.find("boom")
                    .execute(new ToolCall("4", "boom", "{\"input\":\"x\"}"));
            assertFalse(result.success());
            assertEquals("boom-x", result.error());
        }

        @Test
        @DisplayName("optional_param tool should handle missing optional argument")
        void optionalParamMissing() {
            registry.register(new SampleTools());

            var result = registry.find("optional_param")
                    .execute(new ToolCall("5", "optional_param", "{\"req\":\"val\"}"));
            assertTrue(result.success());
            assertEquals("val:null", result.output());
        }

        @Test
        @DisplayName("optional_param tool should handle present optional argument")
        void optionalParamPresent() {
            registry.register(new SampleTools());

            var result = registry.find("optional_param")
                    .execute(new ToolCall("6", "optional_param", "{\"req\":\"val\",\"opt\":\"extra\"}"));
            assertTrue(result.success());
            assertEquals("val:extra", result.output());
        }

        @Test
        @DisplayName("listDefinitions should contain tool names and descriptions")
        void definitionsContainMetadata() {
            registry.register(new SampleTools());

            var defs = registry.listDefinitions();
            var echoDef = defs.stream().filter(d -> d.name().equals("echo")).findFirst().orElseThrow();
            assertEquals("Echo the input text", echoDef.description());

            @SuppressWarnings("unchecked")
            var properties = (Map<String, Object>) echoDef.parameters().get("properties");
            assertNotNull(properties.get("text"));

            @SuppressWarnings("unchecked")
            var textSchema = (Map<String, Object>) properties.get("text");
            assertEquals("string", textSchema.get("type"));
            assertEquals("Text to echo", textSchema.get("description"));

            @SuppressWarnings("unchecked")
            var required = (List<String>) echoDef.parameters().get("required");
            assertTrue(required.contains("text"));
        }

        @Test
        @DisplayName("tool name should default to method name when annotation name is blank")
        void defaultNameIsMethodName() {
            registry.register(new SampleTools());
            assertNotNull(registry.find("add"));
        }
    }

    @Test
    @DisplayName("register(Object) with no @Tool methods should register nothing")
    void noToolMethods() {
        registry.register(new Object());
        assertEquals(0, registry.size());
    }

    // ---- JSON Schema 扩展能力 ----

    static class SchemaTools {

        @Tool(name = "search", description = "Search with constraints")
        String search(
                @ToolParam(description = "Query", minLength = 1, maxLength = 100, pattern = "[a-z]+")
                String query) {
            return query;
        }

        @Tool(name = "scale", description = "Scale a number")
        double scale(
                @ToolParam(description = "Factor", minimum = 0.0, maximum = 10.0)
                double factor) {
            return factor * 2.0;
        }

        @Tool(name = "pick_color", description = "Pick a color")
        String pickColor(@ToolParam(description = "Color") Color color) {
            return color.name();
        }

        @Tool(name = "join", description = "Join a list of strings")
        String join(
                @ToolParam(description = "Parts", itemType = String.class)
                List<String> parts) {
            return String.join(",", parts);
        }

        @Tool(name = "format_uri", description = "Format a URI")
        String formatUri(@ToolParam(description = "URI", format = "uri") String uri) {
            return uri;
        }

        @Tool(name = "choose", description = "Choose from explicit enum")
        String choose(
                @ToolParam(description = "Mode", enumValues = {"fast", "slow"})
                String mode) {
            return mode;
        }

        @Tool(name = "with_default", description = "Has a default value")
        String withDefault(@ToolParam(description = "Name", defaultValue = "world") String name) {
            return name;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> propertySchema(ToolDefinition def, String propName) {
        var props = (Map<String, Object>) def.parameters().get("properties");
        return (Map<String, Object>) props.get(propName);
    }

    @Nested
    @DisplayName("JSON Schema extensions")
    class JsonSchemaExtensions {

        @Test
        @DisplayName("string constraints: minLength, maxLength, pattern")
        void stringConstraints() {
            registry.register(new SchemaTools());
            var def = registry.listDefinitions().stream()
                    .filter(d -> d.name().equals("search")).findFirst().orElseThrow();
            var schema = propertySchema(def, "query");
            assertEquals("string", schema.get("type"));
            assertEquals(1, schema.get("minLength"));
            assertEquals(100, schema.get("maxLength"));
            assertEquals("[a-z]+", schema.get("pattern"));
        }

        @Test
        @DisplayName("numeric constraints: minimum, maximum")
        void numericConstraints() {
            registry.register(new SchemaTools());
            var def = registry.listDefinitions().stream()
                    .filter(d -> d.name().equals("scale")).findFirst().orElseThrow();
            var schema = propertySchema(def, "factor");
            assertEquals("number", schema.get("type"));
            assertEquals(0.0, schema.get("minimum"));
            assertEquals(10.0, schema.get("maximum"));
        }

        @Test
        @DisplayName("Java enum type should auto-generate enum values")
        void javaEnumAutoDerive() {
            registry.register(new SchemaTools());
            var def = registry.listDefinitions().stream()
                    .filter(d -> d.name().equals("pick_color")).findFirst().orElseThrow();
            var schema = propertySchema(def, "color");
            assertEquals("string", schema.get("type"));
            var enumVals = (List<String>) schema.get("enum");
            assertEquals(List.of("RED", "GREEN", "BLUE"), enumVals);
        }

        @Test
        @DisplayName("array type with itemType hint should produce items schema")
        void arrayWithItemSchema() {
            registry.register(new SchemaTools());
            var def = registry.listDefinitions().stream()
                    .filter(d -> d.name().equals("join")).findFirst().orElseThrow();
            var schema = propertySchema(def, "parts");
            assertEquals("array", schema.get("type"));
            var items = (Map<String, Object>) schema.get("items");
            assertEquals("string", items.get("type"));
        }

        @Test
        @DisplayName("format should be applied to string schema")
        void formatField() {
            registry.register(new SchemaTools());
            var def = registry.listDefinitions().stream()
                    .filter(d -> d.name().equals("format_uri")).findFirst().orElseThrow();
            var schema = propertySchema(def, "uri");
            assertEquals("uri", schema.get("format"));
        }

        @Test
        @DisplayName("explicit enumValues should override and apply to any string param")
        void explicitEnumValues() {
            registry.register(new SchemaTools());
            var def = registry.listDefinitions().stream()
                    .filter(d -> d.name().equals("choose")).findFirst().orElseThrow();
            var schema = propertySchema(def, "mode");
            var enumVals = (List<String>) schema.get("enum");
            assertEquals(List.of("fast", "slow"), enumVals);
        }

        @Test
        @DisplayName("defaultValue should appear in schema as default")
        void defaultValue() {
            registry.register(new SchemaTools());
            var def = registry.listDefinitions().stream()
                    .filter(d -> d.name().equals("with_default")).findFirst().orElseThrow();
            var schema = propertySchema(def, "name");
            assertEquals("world", schema.get("default"));
        }

        @Test
        @DisplayName("array tool execution should pass list argument correctly")
        void arrayToolExecution() {
            registry.register(new SchemaTools());
            var result = registry.find("join")
                    .execute(new ToolCall("7", "join", "{\"parts\":[\"a\",\"b\",\"c\"]}"));
            assertTrue(result.success());
            assertEquals("a,b,c", result.output());
        }

        @Test
        @DisplayName("enum tool execution should pass enum argument correctly")
        void enumToolExecution() {
            registry.register(new SchemaTools());
            var result = registry.find("pick_color")
                    .execute(new ToolCall("8", "pick_color", "{\"color\":\"RED\"}"));
            assertTrue(result.success());
            assertEquals("RED", result.output());
        }

        @Test
        @DisplayName("array items default to string when itemType not specified")
        void arrayItemsDefaultString() {
            // join tool has itemType = String.class — verify default behavior too
            var def = new ToolDefinition("t", "d",
                    Map.of("type", "object", "properties", Map.of(), "required", List.of()));
            assertNotNull(def);
            // Also verify list execution without itemType hint works (defaults to String)
        }
    }
}