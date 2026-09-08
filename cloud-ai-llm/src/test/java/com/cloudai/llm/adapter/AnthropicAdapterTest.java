package com.cloudai.llm.adapter;

import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.client.ChatClientFactory;
import com.cloudai.llm.config.ProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

/**
 * Anthropic Messages API 集成测试 — 通过 ChatClient 调用。
 *
 * <p>运行前需设置环境变量：</p>
 * <pre>
 *   ANTHROPIC_BASE_URL=https://your-api-host/v1
 *   ANTHROPIC_API_KEY=sk-xxx
 * </pre>
 * <p>然后删除 {@code @Disabled} 运行。</p>
 */
@Disabled("手动运行 — 需要设置 ANTHROPIC_BASE_URL / ANTHROPIC_API_KEY 环境变量")
class AnthropicAdapterTest {

    private static final String BASE_URL = System.getenv().getOrDefault(
            "ANTHROPIC_BASE_URL", "https://your-api-host/v1");
    private static final String API_KEY = System.getenv().getOrDefault(
            "ANTHROPIC_API_KEY", "sk-your-api-key");
    private static final String MODEL = System.getenv().getOrDefault(
            "ANTHROPIC_MODEL", "deepseek-v4-pro");

    private ModelRouter router;

    @BeforeEach
    void setUp() {
        var props = new ProviderProperties(
                BASE_URL, API_KEY, MODEL,
                Duration.ofSeconds(120), 100_000, null, List.of("chat"));
        var adapter = new AnthropicLlmAdapter(props);
        router = new ModelRouter("anthropic");
        router.register("anthropic", adapter);
        router.validate();
    }

    @Test
    @DisplayName("ChatClient 同步调用")
    void chatClientCall() {
        var client = ChatClientFactory.create(router);

        String result = client.prompt("D:\\wks-vos\\PythonProject 看下这个项目")
                .call()
                .content();

        System.out.println("=== 同步响应 ===");
        System.out.println(result);
    }

    @Test
    @DisplayName("ChatClient 流式调用")
    void chatClientStream() {
        var client = ChatClientFactory.create(router);

        var flux = client.prompt("说一句你好")
                .stream()
                .content();

        StepVerifier.create(flux)
                .recordWith(java.util.ArrayList::new)
                .thenConsumeWhile(x -> true)
                .verifyComplete();
    }
}
