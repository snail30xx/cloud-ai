package com.cloudai.persona.config;

import com.cloudai.persona.advisor.PersonaAdvisor;
import com.cloudai.persona.impl.DefaultPersonaAssembler;
import com.cloudai.persona.impl.DefaultPersonaProvider;
import com.cloudai.persona.spi.PersonaAssembler;
import com.cloudai.persona.spi.PersonaProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PersonaAutoConfiguration")
class PersonaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PersonaAutoConfiguration.class));

    @Nested
    @DisplayName("default behavior (enabled by default)")
    class DefaultBehavior {
        @Test
        @DisplayName("should create core beans")
        void shouldCreateCoreBeans() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(PersonaProvider.class);
                assertThat(context).hasSingleBean(PersonaAssembler.class);
                assertThat(context).hasSingleBean(PersonaProperties.class);
            });
        }

        @Test
        @DisplayName("beans should be default implementations")
        void shouldBeDefaultImplementations() {
            contextRunner.run(context -> {
                assertThat(context.getBean(PersonaProvider.class)).isInstanceOf(DefaultPersonaProvider.class);
                assertThat(context.getBean(PersonaAssembler.class)).isInstanceOf(DefaultPersonaAssembler.class);
            });
        }

        @Test
        @DisplayName("should not create PersonaAdvisor by default")
        void shouldNotCreateAdvisorByDefault() {
            contextRunner.run(context -> {
                assertThat(context).doesNotHaveBean(PersonaAdvisor.class);
            });
        }
    }

    @Nested
    @DisplayName("module disabled")
    class ModuleDisabled {
        @Test
        @DisplayName("should not create any persona beans when enabled=false")
        void shouldNotCreateBeansWhenDisabled() {
            contextRunner.withPropertyValues("cloud-ai.persona.enabled=false").run(context -> {
                assertThat(context).doesNotHaveBean(PersonaProvider.class);
                assertThat(context).doesNotHaveBean(PersonaAssembler.class);
            });
        }
    }

    @Nested
    @DisplayName("advisor optional")
    class AdvisorOptional {
        @Test
        @DisplayName("should create PersonaAdvisor when advisor.enabled=true")
        void shouldCreateAdvisorWhenEnabled() {
            contextRunner.withPropertyValues("cloud-ai.persona.advisor.enabled=true").run(context -> {
                assertThat(context).hasSingleBean(PersonaAdvisor.class);
            });
        }
    }

    @Nested
    @DisplayName("bean replacement")
    class BeanReplacement {
        @Test
        @DisplayName("should allow replacing default PersonaProvider")
        void shouldAllowReplacingPersonaProvider() {
            PersonaProvider custom = new PersonaProvider() {
                @Override
                public java.util.Optional<com.cloudai.persona.model.Persona> findById(String id) {
                    return java.util.Optional.empty();
                }

                @Override
                public com.cloudai.persona.model.Persona defaultPersona() {
                    return com.cloudai.persona.model.Persona.of("custom", "Custom", "custom prompt");
                }
            };
            contextRunner.withBean(PersonaProvider.class, () -> custom).run(context -> {
                assertThat(context).hasSingleBean(PersonaProvider.class);
                assertThat(context.getBean(PersonaProvider.class)).isSameAs(custom);
            });
        }
    }

    @Nested
    @DisplayName("configuration properties")
    class ConfigurationProperties {
        @Test
        @DisplayName("should use built-in default when no personas configured")
        void shouldUseBuiltinDefault() {
            contextRunner.run(context -> {
                var props = context.getBean(PersonaProperties.class);
                assertThat(props.defaultPersonaId()).isEqualTo("default");
            });
        }

        @Test
        @DisplayName("should bind custom persona config")
        void shouldBindCustomPersonaConfig() {
            contextRunner
                    .withPropertyValues(
                            "cloud-ai.persona.default-persona-id=dev",
                            "cloud-ai.persona.personas.dev.name=Developer",
                            "cloud-ai.persona.personas.dev.role=software engineer",
                            "cloud-ai.persona.personas.dev.system-prompt=You write clean code.",
                            "cloud-ai.persona.personas.dev.guidelines[0]=always test",
                            "cloud-ai.persona.personas.dev.tone-style=concise",
                            "cloud-ai.persona.personas.dev.constraints[0]=never skip tests")
                    .run(context -> {
                        var props = context.getBean(PersonaProperties.class);
                        assertThat(props.defaultPersonaId()).isEqualTo("dev");
                        assertThat(props.personas()).containsKey("dev");
                        assertThat(props.personas().get("dev").name()).isEqualTo("Developer");
                        assertThat(props.personas().get("dev").role()).isEqualTo("software engineer");

                        var provider = context.getBean(PersonaProvider.class);
                        var persona = provider.findById("dev");
                        assertThat(persona).isPresent();
                        assertThat(persona.get().name()).isEqualTo("Developer");
                    });
        }
    }
}
