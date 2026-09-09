package com.cloudai.persona.advisor;

import com.cloudai.core.model.ChatRequest;
import com.cloudai.core.model.Message;
import com.cloudai.persona.impl.DefaultPersonaAssembler;
import com.cloudai.persona.model.Persona;
import com.cloudai.persona.spi.PersonaAssembler;
import com.cloudai.persona.spi.PersonaProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PersonaAdvisor")
class PersonaAdvisorTest {

    private PersonaProvider provider;
    private PersonaAssembler assembler;
    private PersonaAdvisor advisor;

    @BeforeEach
    void setUp() {
        provider = new PersonaProvider() {
            private final Persona persona = new Persona(
                    "dev", "Developer", "software engineer",
                    "You write clean code.",
                    List.of("always test"), "concise",
                    List.of("never skip tests"));

            @Override
            public Optional<Persona> findById(String personaId) {
                return "dev".equals(personaId) ? Optional.of(persona) : Optional.empty();
            }

            @Override
            public Persona defaultPersona() {
                return persona;
            }
        };
        assembler = new DefaultPersonaAssembler();
        advisor = new PersonaAdvisor(provider, assembler);
    }

    @Nested
    @DisplayName("system prompt injection")
    class SystemPromptInjection {
        @Test
        @DisplayName("should inject persona prompt at head when no system message")
        void shouldInjectAtHeadWhenNoSystemMessage() {
            var request = new ChatRequest(
                    List.of(Message.user("hello")),
                    null, null);
            var result = advisor.advise(request, req -> req);
            assertEquals(2, result.messages().size());
            assertTrue(result.messages().get(0).isSystem());
            assertTrue(result.messages().get(0).content().contains("You write clean code."));
            assertTrue(result.messages().get(1).isUser());
        }

        @Test
        @DisplayName("should inject after existing system message")
        void shouldInjectAfterExistingSystemMessage() {
            var request = new ChatRequest(
                    List.of(Message.system("base system"), Message.user("hello")),
                    null, null);
            var result = advisor.advise(request, req -> req);
            assertEquals(3, result.messages().size());
            assertEquals("base system", result.messages().get(0).content());
            assertTrue(result.messages().get(1).isSystem());
            assertTrue(result.messages().get(1).content().contains("You write clean code."));
            assertTrue(result.messages().get(2).isUser());
        }

        @Test
        @DisplayName("should preserve tools and options")
        void shouldPreserveToolsAndOptions() {
            var request = new ChatRequest(
                    List.of(Message.user("hello")),
                    null, null);
            var result = advisor.advise(request, req -> req);
              assertNotNull(result.tools()); // ChatRequest converts null to empty list
              assertNull(result.options()); // options is nullable, null preserved
        }

        @Test
        @DisplayName("should pass through chain")
        void shouldPassThroughChain() {
            var request = new ChatRequest(
                    List.of(Message.user("hello")),
                    null, null);
            var result = advisor.advise(request, req -> {
                // Verify the request was modified by the advisor
                assertEquals(2, req.messages().size());
                return req;
            });
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {
        @Test
        @DisplayName("should reject null provider")
        void shouldRejectNullProvider() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PersonaAdvisor(null, assembler));
        }

        @Test
        @DisplayName("should reject null assembler")
        void shouldRejectNullAssembler() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PersonaAdvisor(provider, null));
        }
    }
}

