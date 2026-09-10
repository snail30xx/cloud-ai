package com.cloudai.server;

import com.cloudai.core.chat.ChatModel;
import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.FinishReason;
import com.cloudai.core.chat.TokenUsage;
import com.cloudai.llm.config.LlmProperties;
import com.cloudai.server.service.AgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CloudAiApplication")
class CloudAiApplicationTest {

    @Nested
    @DisplayName("parseDuration")
    class ParseDuration {

        @ParameterizedTest(name = "{0} -> {1}ms")
        @CsvSource({
                "500ms, 500",
                "60s, 60000",
                "10m, 600000",
                "2h, 7200000",
                "90, 90000",
                "PT5M, 300000"
        })
        void parsesSupportedFormats(String input, long expectedMillis) {
            assertEquals(Duration.ofMillis(expectedMillis), CloudAiApplication.parseDuration(input));
        }

        @Test
        @DisplayName("非法值抛 IllegalArgumentException")
        void invalidDurationFailsFast() {
            assertThrows(IllegalArgumentException.class,
                    () -> CloudAiApplication.parseDuration("soon"));
        }
    }

    @Nested
    @DisplayName("resolvePlaceholders")
    class ResolvePlaceholders {

        @Test
        @DisplayName("未设置的环境变量取默认值")
        void unsetEnvUsesFallback() {
            assertEquals("fallback",
                    CloudAiApplication.resolvePlaceholders("${CLOUD_AI_UNSET_VAR_XYZ:fallback}"));
        }

        @Test
        @DisplayName("已设置的环境变量取实际值（PATH 必然存在）")
        void setEnvUsesValue() {
            var resolved = CloudAiApplication.resolvePlaceholders("pre-${PATH:default}-post");
            assertTrue(!resolved.contains("default") && resolved.startsWith("pre-") && resolved.endsWith("-post"));
        }

        @Test
        @DisplayName("无占位符的值原样返回")
        void plainValueUnchanged() {
            assertEquals("https://api.example.com", CloudAiApplication.resolvePlaceholders("https://api.example.com"));
        }
    }

    @Nested
    @DisplayName("buildLlmProperties")
    class BuildLlmProperties {

        @Test
        @DisplayName("从扁平键聚合多个 provider")
        void aggregatesFlatKeys() {
            var props = new Properties();
            props.setProperty("cloud-ai.llm.providers.openai.base-url", "https://api.openai.com/v1");
            props.setProperty("cloud-ai.llm.providers.openai.api-key", "sk-1");
            props.setProperty("cloud-ai.llm.providers.openai.model", "gpt-4o");
            props.setProperty("cloud-ai.llm.providers.openai.timeout", "60s");
            props.setProperty("cloud-ai.llm.providers.deepseek.base-url", "https://api.deepseek.com");
            props.setProperty("cloud-ai.llm.providers.deepseek.api-key", "sk-2");
            props.setProperty("cloud-ai.llm.providers.deepseek.model", "deepseek-v4-pro");
            props.setProperty("cloud-ai.llm.providers.deepseek.capabilities", "chat, tool_calling, thinking");

            var llmProps = CloudAiApplication.buildLlmProperties(props);

            assertEquals(2, llmProps.providers().size());
            assertEquals("gpt-4o", llmProps.providers().get("openai").model());
            assertEquals(Duration.ofSeconds(60), llmProps.providers().get("openai").timeout());
            assertEquals(List.of("chat", "tool_calling", "thinking"),
                    llmProps.providers().get("deepseek").capabilities());
        }

        @Test
        @DisplayName("无 provider 时 fail fast 并给出配置指引")
        void noProvidersFailsFast() {
            var ex = assertThrows(IllegalStateException.class,
                    () -> CloudAiApplication.buildLlmProperties(new Properties()));
            assertTrue(ex.getMessage().contains("cloud-ai.llm.providers"));
        }

        @Test
        @DisplayName("default-provider 缺省取第一个 provider")
        void defaultProviderFallsBackToFirst() {
            var props = new Properties();
            props.setProperty("cloud-ai.llm.providers.openai.base-url", "https://api.openai.com/v1");
            props.setProperty("cloud-ai.llm.providers.openai.api-key", "sk-1");
            props.setProperty("cloud-ai.llm.providers.openai.model", "gpt-4o");

            assertEquals("openai", CloudAiApplication.buildLlmProperties(props).defaultProvider());
        }
    }

    @Nested
    @DisplayName("端到端：装配 + HTTP 服务")
    class EndToEnd {

        private HttpServer server;
        private final ObjectMapper mapper = new ObjectMapper();
        private final HttpClient client = HttpClient.newHttpClient();

        @AfterEach
        void tearDown() {
            if (server != null) {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("POST /api/agent/run 返回 200 与桩模型回答")
        void runEndpointReturnsStubAnswer() throws Exception {
            var tempDir = Files.createTempDirectory("cloud-ai-server-test");
            var props = baseProps(tempDir);

            var agentService = CloudAiApplication.assemble(props, new StubChatModel());
            server = CloudAiApplication.startServer(0, agentService, null);

            var response = postRun(server.getAddress().getPort());

            assertEquals(200, response.statusCode());
            JsonNode body = mapper.readTree(response.body());
            assertEquals("stub-answer", body.get("content").asText());
            assertEquals("COMPLETED", body.get("finishStatus").asText());
        }

        @Test
        @DisplayName("loadProperties 解析占位符后的配置可完成装配")
        void loadPropertiesYieldsUsableConfig() {
            var props = CloudAiApplication.loadProperties();

            assertNotNull(props.getProperty("cloud-ai.llm.providers.openai.base-url"));
            // ${OPENAI_API_KEY:} 未设置时解析为空串（真实部署需提供）
            assertTrue(props.getProperty("cloud-ai.llm.providers.openai.base-url").startsWith("https://"));
        }

        private static Properties baseProps(Path workDir) {
            var props = new Properties();
            props.setProperty("cloud-ai.context.work-dir", workDir.toString());
            props.setProperty("cloud-ai.skills.enabled", "false");
            props.setProperty("cloud-ai.execution.filesystem.workspace",
                    workDir.resolve("workspace").toString());
            return props;
        }

        private HttpResponse<String> postRun(int port) throws IOException, InterruptedException {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/agent/run"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"prompt\":\"hello\"}"))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /** 单轮直答桩模型：不调用工具，直接返回固定文本。 */
    private static final class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(ChatRequest request) {
            return ChatResponse.of("stub-answer", List.of(), new TokenUsage(1, 1), FinishReason.STOP);
        }
    }
}
