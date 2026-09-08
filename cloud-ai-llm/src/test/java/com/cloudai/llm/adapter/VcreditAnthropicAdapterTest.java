package com.cloudai.llm.adapter;

import com.cloudai.llm.ModelRouter;
import com.cloudai.llm.client.ChatClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

/**
 * Anthropic Messages API 集成测试 — 通过 ChatClient 调用。
 *
 * <p>运行前需设置环境变量：</p>
 * <pre>
 *   VC_ANTHROPIC_BASE_URL=https://your-api-host/v1
 *   VC_ANTHROPIC_API_KEY=sk-xxx
 * </pre>
 * <p>然后删除 {@code @Disabled} 运行。</p>
 */
@Disabled("手动运行 — 需要设置 VC_ANTHROPIC_BASE_URL / VC_ANTHROPIC_API_KEY 环境变量")
class VcreditAnthropicAdapterTest {

    private static final String BASE_URL = System.getenv().getOrDefault(
            "VC_ANTHROPIC_BASE_URL", "https://your-api-host/v1");
    private static final String API_KEY = System.getenv().getOrDefault(
            "VC_ANTHROPIC_API_KEY", "sk-your-api-key");
    private static final String MODEL = System.getenv().getOrDefault(
            "VC_ANTHROPIC_MODEL", "deepseek-v4-pro");

    private ModelRouter router;

    @BeforeEach
    void setUp() {
        var adapter = new VcreditAnthropicAdapter("vcredit", MODEL, BASE_URL, API_KEY,
                100_000, Duration.ofSeconds(120));
        router = new ModelRouter("vcredit");
        router.register("vcredit", adapter);
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

        // 流式内容已通过 recordWith 收集，此处不再打印
    }
}