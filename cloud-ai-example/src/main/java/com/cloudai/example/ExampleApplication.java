package com.cloudai.example;

import com.cloudai.memory.model.MemoryType;
import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.server.facade.CloudAi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cloud AI 端到端示例。
 *
 * <p>自动加载项目根目录的 .env 文件，读取 API Key 和模型配置。
 * <ul>
 *   <li>设置了 OPENAI_API_KEY → 接入真实 OpenAI</li>
 *   <li>设置了 DEEPSEEK_API_KEY → 接入真实 DeepSeek</li>
 *   <li>都没设置 → 使用 Stub LLM 模拟</li>
 * </ul>
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 * mvn install -DskipTests
 * mvn compile exec:java -pl cloud-ai-example
 * }</pre>
 *
 * <h3>.env 配置</h3>
 * <pre>
 * OPENAI_API_KEY=sk-xxx
 * OPENAI_BASE_URL=https://api.openai.com/v1
 * OPENAI_MODEL=gpt-4o
 * </pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class ExampleApplication {

    private static final Logger log = LoggerFactory.getLogger(ExampleApplication.class);

    public static void main(String[] args) {
        // 加载 .env 文件
        var env = Dotenv.load();
        if (env.isEmpty()) {
            log.info("No .env file found, falling back to system environment variables");
        } else {
            log.info("Loaded .env: {} key(s)", env.size());
        }

        var openaiKey = Dotenv.get("OPENAI_API_KEY");
        var deepseekKey = Dotenv.get("DEEPSEEK_API_KEY");

        CloudAi agent;

        if (openaiKey != null && !openaiKey.isBlank()) {
            var url = Dotenv.get("OPENAI_BASE_URL", "https://api.openai.com/v1");
            var model = Dotenv.get("OPENAI_MODEL", "gpt-4o");
            log.info("Using real OpenAI: url={}, model={}", url, model);
            agent = CloudAi.builder()
                    .openai(url, openaiKey, model)
                    .tool("calculator", "Evaluate an arithmetic expression (e.g. 25 * 4)",
                            new CalculatorToolExecutor())
                    .persona("Math Assistant", "You are a helpful math assistant. Use the calculator tool for arithmetic.")
                    .memory("user is a Java developer interested in mathematics", MemoryType.SEMANTIC, 0.8)
                    .build();
        } else if (deepseekKey != null && !deepseekKey.isBlank()) {
            var url = Dotenv.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com");
            var model = Dotenv.get("DEEPSEEK_MODEL", "deepseek-v4-pro");
            log.info("Using real DeepSeek: url={}, model={}", url, model);
            agent = CloudAi.builder()
                    .deepseek(url, deepseekKey, model)
                    .tool("calculator", "Evaluate an arithmetic expression (e.g. 25 * 4)",
                            new CalculatorToolExecutor())
                    .persona("Math Assistant", "You are a helpful math assistant. Use the calculator tool for arithmetic.")
                    .memory("user is a Java developer interested in mathematics", MemoryType.SEMANTIC, 0.8)
                    .build();
        } else {
            log.info("No API key found in .env or environment, using Stub LLM for demo");
            agent = CloudAi.builder()
                    .model(new StubChatModel(), "stub")
                    .tool("calculator", "Evaluate an arithmetic expression (e.g. 25 * 4)",
                            new CalculatorToolExecutor())
                    .persona("Math Assistant", "You are a helpful math assistant. Use the calculator tool for arithmetic.")
                    .memory("user is a Java developer interested in mathematics", MemoryType.SEMANTIC, 0.8)
                    .build();
        }

        // --- 运行 ---
        var response = agent.run("calculate 25 * 4");

        // --- 输出 ---
        log.info("=== Response ===");
        log.info("  status   : {}", response.finishStatus());
        log.info("  answer   : {}", response.content());
        log.info("  turns    : {}", response.turnsExecuted());
        log.info("  toolCalls: {}", response.toolCallsExecuted());
        if (response.totalUsage() != null) {
            log.info("  tokens   : {}", response.totalUsage().totalTokens());
        }
    }
}
