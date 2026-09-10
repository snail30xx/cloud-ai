package com.cloudai.llm;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.ModelInfo;
import com.cloudai.core.chat.TokenUsage;
import com.cloudai.core.chat.ChatModel;
import com.cloudai.core.chat.ModelDiscovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelRouter 单元测试。
 */
class ModelRouterTest {

    private ModelRouter router;
    private ChatModel mockModel;
    private ChatModel mockModel2;

    @BeforeEach
    void setUp() {
        router = new ModelRouter("default-model");
        mockModel = new MockChatModel("test", "test-model", 10000, List.of("chat"), "mock");
        mockModel2 = new MockChatModel("test2", "test-model-2", 8000, List.of("chat", "tool_calling"), "mock2");
    }

    /** 同时实现 ChatModel 和 ModelDiscovery 的测试辅助类 */
    private static class MockChatModel implements ChatModel, ModelDiscovery {
        private final ModelInfo info;
        private final String content;

        MockChatModel(String provider, String model, int maxTokens, List<String> capabilities, String content) {
            this.info = new ModelInfo(provider, model, maxTokens, capabilities);
            this.content = content;
        }

        @Override
        public ChatResponse call(ChatRequest request) {
            return ChatResponse.of(content, List.of(), new TokenUsage(0, 0), FinishReason.STOP);
        }

        @Override
        public ModelInfo getModelInfo() { return info; }

        @Override
        public List<ModelInfo> listModels() { return List.of(info); }
    }

    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("should register and resolve model")
        void shouldRegisterAndResolve() {
            router.register("test-model", mockModel);

            var resolved = router.resolve("test-model");
            assertSame(mockModel, resolved);
        }

        @Test
        @DisplayName("should throw on duplicate registration")
        void shouldThrowOnDuplicateRegistration() {
            router.register("test-model", mockModel);

            assertThrows(IllegalStateException.class, () ->
                    router.register("test-model", mockModel));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should throw on blank or null name")
        void shouldThrowOnBlankOrNullName(String name) {
            assertThrows(IllegalArgumentException.class, () ->
                    router.register(name, mockModel));
        }

        @Test
        @DisplayName("should throw on null model")
        void shouldThrowOnNullModel() {
            assertThrows(IllegalArgumentException.class, () ->
                    router.register("test-model", null));
        }
    }

    @Nested
    @DisplayName("Resolution")
    class ResolutionTests {

        @Test
        @DisplayName("should throw when model not found")
        void shouldThrowWhenModelNotFound() {
            var ex = assertThrows(IllegalStateException.class, () ->
                    router.resolve("non-existent"));
            assertTrue(ex.getMessage().contains("non-existent"));
        }

        @Test
        @DisplayName("should resolve default model")
        void shouldResolveDefaultModel() {
            router.register("default-model", mockModel);

            var resolved = router.getDefault();
            assertSame(mockModel, resolved);
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("should pass when default model is registered")
        void shouldPassWhenDefaultModelRegistered() {
            router.register("default-model", mockModel);
            assertDoesNotThrow(() -> router.validate());
        }

        @Test
        @DisplayName("should throw when default model is not registered")
        void shouldThrowWhenDefaultModelNotRegistered() {
            router.register("other-model", mockModel);
            assertThrows(IllegalStateException.class, () -> router.validate());
        }

        @Test
        @DisplayName("should throw when no models registered")
        void shouldThrowWhenNoModelsRegistered() {
            assertThrows(IllegalStateException.class, () -> router.validate());
        }
    }

    @Nested
    @DisplayName("List models")
    class ListModelsTests {

        @Test
        @DisplayName("should aggregate models from all registered models")
        void shouldAggregateModelsFromAllModels() {
            router.register("z-provider", mockModel);
            router.register("a-provider", mockModel2);

            var models = router.listModels();

            assertEquals(2, models.size());
            assertEquals("test", models.get(0).provider());
            assertEquals("test2", models.get(1).provider());
        }

        @Test
        @DisplayName("should return empty list when no models")
        void shouldReturnEmptyListWhenNoModels() {
            var models = router.listModels();
            assertTrue(models.isEmpty());
        }
    }
}