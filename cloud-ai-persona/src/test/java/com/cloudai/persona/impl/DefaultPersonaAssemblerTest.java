package com.cloudai.persona.impl;

import com.cloudai.core.model.ToolDefinition;
import com.cloudai.persona.model.Persona;
import com.cloudai.persona.spi.PersonaAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultPersonaAssembler")
class DefaultPersonaAssemblerTest {

    private PersonaAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new DefaultPersonaAssembler();
    }

    @Nested
    @DisplayName("assembly")
    class Assembly {
        @Test
        @DisplayName("should include systemPrompt")
        void shouldIncludeSystemPrompt() {
            var persona = Persona.of("id", "name", "You are a helper.");
            var result = assembler.assemble(persona, null);
            assertTrue(result.contains("You are a helper."));
        }

        @Test
        @DisplayName("should include role when present")
        void shouldIncludeRole() {
            var persona = new Persona("id", "name", "code reviewer",
                    "prompt", List.of(), null, List.of());
            var result = assembler.assemble(persona, null);
            assertTrue(result.contains("You are a code reviewer."));
        }

        @Test
        @DisplayName("should include guidelines")
        void shouldIncludeGuidelines() {
            var persona = new Persona("id", "name", null, "prompt",
                    List.of("be honest", "be thorough"), null, List.of());
            var result = assembler.assemble(persona, null);
            assertTrue(result.contains("Guidelines:"));
            assertTrue(result.contains("be honest"));
            assertTrue(result.contains("be thorough"));
        }

        @Test
        @DisplayName("should include toneStyle when present")
        void shouldIncludeToneStyle() {
            var persona = new Persona("id", "name", null, "prompt",
                    List.of(), "friendly and casual", List.of());
            var result = assembler.assemble(persona, null);
            assertTrue(result.contains("Tone: friendly and casual."));
        }

        @Test
        @DisplayName("should include constraints")
        void shouldIncludeConstraints() {
            var persona = new Persona("id", "name", null, "prompt",
                    List.of(), null, List.of("never share secrets", "no destructive ops"));
            var result = assembler.assemble(persona, null);
            assertTrue(result.contains("Constraints:"));
            assertTrue(result.contains("never share secrets"));
            assertTrue(result.contains("no destructive ops"));
        }

        @Test
        @DisplayName("should include tool descriptions")
        void shouldIncludeTools() {
            var persona = Persona.of("id", "name", "prompt");
            var tools = List.of(
                    new ToolDefinition("file_read", "Read file contents", Map.of()),
                    new ToolDefinition("shell_exec", "Execute shell command", Map.of()));
            var result = assembler.assemble(persona, tools);
            assertTrue(result.contains("Available tools:"));
            assertTrue(result.contains("file_read: Read file contents"));
            assertTrue(result.contains("shell_exec: Execute shell command"));
        }

        @Test
        @DisplayName("should not include tool section when tools is null")
        void shouldNotIncludeToolsWhenNull() {
            var persona = Persona.of("id", "name", "prompt");
            var result = assembler.assemble(persona, null);
            assertFalse(result.contains("Available tools:"));
        }

        @Test
        @DisplayName("should not include tool section when tools is empty")
        void shouldNotIncludeToolsWhenEmpty() {
            var persona = Persona.of("id", "name", "prompt");
            var result = assembler.assemble(persona, List.of());
            assertFalse(result.contains("Available tools:"));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {
        @Test
        @DisplayName("should reject null persona")
        void shouldRejectNullPersona() {
            assertThrows(IllegalArgumentException.class, () -> assembler.assemble(null, null));
        }
    }
}
