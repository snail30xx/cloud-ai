package com.cloudai.llm.config;

import com.cloudai.core.chat.ChatModel;
import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.adapter.AnthropicLlmAdapter;
import com.cloudai.llm.adapter.DeepSeekLlmAdapter;
import com.cloudai.llm.adapter.OpenAiLlmAdapter;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LlmAutoConfiguration 适配器选择")
class LlmAutoConfigurationTest {

    private static ProviderProperties provider(String baseUrl, List<String> capabilities) {
        return new ProviderProperties(baseUrl, "sk-test", "test-model",
                Duration.ofSeconds(30), 100_000, null, capabilities);
    }

    @Nested
    @DisplayName("createAdapter 分支")
    class AdapterSelection {

        @Test
        @DisplayName("provider 名为 anthropic → AnthropicLlmAdapter（无论 capabilities）")
        void anthropicByName() {
            var adapter = LlmAutoConfiguration.createAdapter(
                    "Anthropic", provider("https://api.anthropic.com", List.of("chat")),
                    ObservationRegistry.NOOP, null);

            assertInstanceOf(AnthropicLlmAdapter.class, adapter);
        }

        @Test
        @DisplayName("capabilities 含 anthropic → AnthropicLlmAdapter")
        void anthropicByCapability() {
            var adapter = LlmAutoConfiguration.createAdapter(
                    "my-proxy", provider("https://example.com", List.of("chat", "anthropic")),
                    ObservationRegistry.NOOP, null);

            assertInstanceOf(AnthropicLlmAdapter.class, adapter);
        }

        @Test
        @DisplayName("capabilities 含 thinking → DeepSeekLlmAdapter（优先级低于 anthropic）")
        void deepSeekByThinking() {
            var adapter = LlmAutoConfiguration.createAdapter(
                    "deepseek", provider("https://api.deepseek.com", List.of("chat", "thinking")),
                    ObservationRegistry.NOOP, null);

            assertInstanceOf(DeepSeekLlmAdapter.class, adapter);
        }

        @Test
        @DisplayName("其余 → OpenAiLlmAdapter")
        void openAiDefault() {
            var adapter = LlmAutoConfiguration.createAdapter(
                    "openai", provider("https://api.openai.com/v1", List.of("chat", "tool_calling")),
                    ObservationRegistry.NOOP, null);

            assertInstanceOf(OpenAiLlmAdapter.class, adapter);
        }
    }

    @Nested
    @DisplayName("modelRouter 装配")
    class RouterAssembly {

        @Test
        @DisplayName("多 provider 注册 + 默认 provider 校验")
        void multipleProvidersRegistered() {
            var props = new LlmProperties("openai", Map.of(
                    "openai", provider("https://api.openai.com/v1", List.of("chat")),
                    "anthropic", provider("https://api.anthropic.com", List.of("chat"))));

            var router = LlmAutoConfiguration.modelRouter(props, null, null);

            assertInstanceOf(OpenAiLlmAdapter.class, router.getDefault());
            assertInstanceOf(AnthropicLlmAdapter.class, router.resolve("anthropic"));
        }

        @Test
        @DisplayName("缺少默认 provider → 启动失败（fail fast）")
        void missingDefaultProviderFails() {
            var props = new LlmProperties("", Map.of(
                    "openai", provider("https://api.openai.com/v1", List.of("chat"))));

            assertThrows(IllegalStateException.class,
                    () -> LlmAutoConfiguration.modelRouter(props, null, null));
        }

        @Test
        @DisplayName("无任何 provider → 启动失败（fail fast）")
        void noProvidersFails() {
            var props = new LlmProperties("openai", Map.of());

            var ex = assertThrows(IllegalStateException.class,
                    () -> LlmAutoConfiguration.modelRouter(props, null, null));
            assertTrue(ex.getMessage().contains("providers"));
        }
    }
}
