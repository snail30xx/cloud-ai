package com.cloudai.memory.config;

import com.cloudai.memory.advisor.MemoryAdvisor;
import com.cloudai.memory.impl.InMemoryMemoryStore;
import com.cloudai.memory.impl.KeywordMemoryRetriever;
import com.cloudai.memory.impl.SimpleContextManager;
import com.cloudai.memory.spi.ContextManager;
import com.cloudai.memory.spi.MemoryRetriever;
import com.cloudai.memory.spi.MemoryStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MemoryAutoConfiguration")
class MemoryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MemoryAutoConfiguration.class));

    @Nested
    @DisplayName("default behavior (enabled by default)")
    class DefaultBehavior {
        @Test
        @DisplayName("should create core beans")
        void shouldCreateCoreBeans() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(MemoryStore.class);
                assertThat(context).hasSingleBean(MemoryRetriever.class);
                assertThat(context).hasSingleBean(ContextManager.class);
                assertThat(context).hasSingleBean(MemoryProperties.class);
            });
        }

        @Test
        @DisplayName("beans should be default implementations")
        void shouldBeDefaultImplementations() {
            contextRunner.run(context -> {
                assertThat(context.getBean(MemoryStore.class)).isInstanceOf(InMemoryMemoryStore.class);
                assertThat(context.getBean(MemoryRetriever.class)).isInstanceOf(KeywordMemoryRetriever.class);
                assertThat(context.getBean(ContextManager.class)).isInstanceOf(SimpleContextManager.class);
            });
        }

        @Test
        @DisplayName("should not create MemoryAdvisor by default")
        void shouldNotCreateAdvisorByDefault() {
            contextRunner.run(context -> {
                assertThat(context).doesNotHaveBean(MemoryAdvisor.class);
            });
        }
    }

    @Nested
    @DisplayName("module disabled")
    class ModuleDisabled {
        @Test
        @DisplayName("should not create any memory beans when enabled=false")
        void shouldNotCreateBeansWhenDisabled() {
            contextRunner.withPropertyValues("cloud-ai.memory.enabled=false").run(context -> {
                assertThat(context).doesNotHaveBean(MemoryStore.class);
                assertThat(context).doesNotHaveBean(MemoryRetriever.class);
                assertThat(context).doesNotHaveBean(ContextManager.class);
            });
        }
    }

    @Nested
    @DisplayName("advisor optional")
    class AdvisorOptional {
        @Test
        @DisplayName("should create MemoryAdvisor when advisor.enabled=true")
        void shouldCreateAdvisorWhenEnabled() {
            contextRunner.withPropertyValues("cloud-ai.memory.advisor.enabled=true").run(context -> {
                assertThat(context).hasSingleBean(MemoryAdvisor.class);
            });
        }
    }

    @Nested
    @DisplayName("bean replacement")
    class BeanReplacement {
        @Test
        @DisplayName("should allow replacing default MemoryStore")
        void shouldAllowReplacingMemoryStore() {
            MemoryStore custom = new InMemoryMemoryStore();
            contextRunner.withBean(MemoryStore.class, () -> custom).run(context -> {
                assertThat(context).hasSingleBean(MemoryStore.class);
                assertThat(context.getBean(MemoryStore.class)).isSameAs(custom);
            });
        }
    }

    @Nested
    @DisplayName("configuration properties")
    class ConfigurationProperties {
        @Test
        @DisplayName("should use defaults when not configured")
        void shouldUseDefaults() {
            contextRunner.run(context -> {
                var props = context.getBean(MemoryProperties.class);
                assertThat(props.maxContextTokens()).isEqualTo(8000);
                assertThat(props.maxRetrievalResults()).isEqualTo(5);
                assertThat(props.defaultAgentId()).isEqualTo("cloud-ai-agent");
            });
        }

        @Test
        @DisplayName("should bind custom properties")
        void shouldBindCustomProperties() {
            contextRunner
                    .withPropertyValues(
                            "cloud-ai.memory.max-context-tokens=4000",
                            "cloud-ai.memory.max-retrieval-results=10",
                            "cloud-ai.memory.default-agent-id=my-agent")
                    .run(context -> {
                        var props = context.getBean(MemoryProperties.class);
                        assertThat(props.maxContextTokens()).isEqualTo(4000);
                        assertThat(props.maxRetrievalResults()).isEqualTo(10);
                        assertThat(props.defaultAgentId()).isEqualTo("my-agent");
                    });
        }
    }
}
