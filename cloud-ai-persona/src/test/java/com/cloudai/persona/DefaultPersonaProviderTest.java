package com.cloudai.persona;

import com.cloudai.persona.config.PersonaProperties;
import com.cloudai.persona.config.PersonaProperties.PersonaConfig;
import com.cloudai.persona.Persona;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultPersonaProvider")
class DefaultPersonaProviderTest {

    @Nested
    @DisplayName("with configured personas")
    class ConfiguredPersonas {
        @Test
        @DisplayName("should load personas from properties")
        void shouldLoadFromProperties() {
            var config = new PersonaConfig(
                    "Developer", "software engineer", "You write clean code.",
                    List.of("always test your code"), "concise",
                    List.of("never skip tests"));
            var props = new PersonaProperties("dev", Map.of("dev", config));
            var provider = new DefaultPersonaProvider(props);

            var found = provider.findById("dev");
            assertTrue(found.isPresent());
            assertEquals("Developer", found.get().name());
            assertEquals("software engineer", found.get().role());
        }

        @Test
        @DisplayName("should return default persona")
        void shouldReturnDefaultPersona() {
            var config = new PersonaConfig("Dev", null, "You write code.", null, null, null);
            var props = new PersonaProperties("dev", Map.of("dev", config));
            var provider = new DefaultPersonaProvider(props);

            var persona = provider.defaultPersona();
            assertEquals("dev", persona.id());
        }

        @Test
        @DisplayName("should return empty for non-existent id")
        void shouldReturnEmptyForNonExistent() {
            var props = new PersonaProperties("default", Map.of());
            var provider = new DefaultPersonaProvider(props);
            assertTrue(provider.findById("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("should use id as name when name is blank")
        void shouldUseIdAsNameWhenBlank() {
            var config = new PersonaConfig(null, null, "prompt", null, null, null);
            var props = new PersonaProperties("myid", Map.of("myid", config));
            var provider = new DefaultPersonaProvider(props);
            assertEquals("myid", provider.findById("myid").get().name());
        }

        @Test
        @DisplayName("should use fallback systemPrompt when blank")
        void shouldUseFallbackSystemPrompt() {
            var config = new PersonaConfig("Name", null, null, null, null, null);
            var props = new PersonaProperties("id", Map.of("id", config));
            var provider = new DefaultPersonaProvider(props);
            assertTrue(provider.findById("id").get().systemPrompt().contains("helpful AI assistant"));
        }
    }

    @Nested
    @DisplayName("with empty config")
    class EmptyConfig {
        @Test
        @DisplayName("should use built-in default when no personas configured")
        void shouldUseBuiltinDefault() {
            var props = new PersonaProperties("default", Map.of());
            var provider = new DefaultPersonaProvider(props);
            var persona = provider.defaultPersona();
            assertEquals("default", persona.id());
            assertFalse(persona.systemPrompt().isBlank());
            assertFalse(persona.guidelines().isEmpty());
            assertFalse(persona.constraints().isEmpty());
        }

        @Test
        @DisplayName("built-in default should have sensible defaults")
        void builtinDefaultShouldHaveDefaults() {
            var builtin = DefaultPersonaProvider.builtinDefault();
            assertEquals("default", builtin.id());
            assertEquals("Cloud AI Assistant", builtin.name());
            assertNotNull(builtin.role());
            assertFalse(builtin.guidelines().isEmpty());
            assertNotNull(builtin.toneStyle());
            assertFalse(builtin.constraints().isEmpty());
        }
    }
}
