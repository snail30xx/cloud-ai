package com.cloudai.persona;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Persona")
class PersonaTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {
        @Test
        @DisplayName("of() should create minimal persona")
        void shouldCreateMinimalPersona() {
            var persona = Persona.of("dev", "Developer", "You write code.");
            assertEquals("dev", persona.id());
            assertEquals("Developer", persona.name());
            assertEquals("You write code.", persona.systemPrompt());
            assertTrue(persona.guidelines().isEmpty());
            assertTrue(persona.constraints().isEmpty());
            assertNull(persona.role());
            assertNull(persona.toneStyle());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {
        @Test
        @DisplayName("should reject blank id")
        void shouldRejectBlankId() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Persona("", "name", null, "prompt", null, null, null));
        }

        @Test
        @DisplayName("should reject blank name")
        void shouldRejectBlankName() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Persona("id", "", null, "prompt", null, null, null));
        }

        @Test
        @DisplayName("should reject blank systemPrompt")
        void shouldRejectBlankSystemPrompt() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Persona("id", "name", null, "", null, null, null));
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {
        @Test
        @DisplayName("should copy guidelines list")
        void shouldCopyGuidelines() {
            var guidelines = new java.util.ArrayList<>(List.of("be honest"));
            var persona = new Persona("id", "name", null, "prompt", guidelines, null, null);
            guidelines.add("mutated");
            assertEquals(1, persona.guidelines().size());
        }

        @Test
        @DisplayName("should handle null guidelines and constraints")
        void shouldHandleNullGuidelinesAndConstraints() {
            var persona = new Persona("id", "name", null, "prompt", null, null, null);
            assertTrue(persona.guidelines().isEmpty());
            assertTrue(persona.constraints().isEmpty());
        }
    }
}
