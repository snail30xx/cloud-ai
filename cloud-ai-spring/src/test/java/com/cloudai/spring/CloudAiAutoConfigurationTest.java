package com.cloudai.spring;

import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.server.service.AgentService;
import com.cloudai.spring.config.CloudAiAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cloud-ai-spring 自动装配上下文测试 — 验证默认装配链路。
 *
 * <p>LLM 配置指向占位端点（不发真实请求），仅验证 Bean 装配。
 * skills.work-dir 指向不存在的目录以保证无技能分支的确定性。</p>
 */
@DisplayName("默认装配：核心 Bean 就绪")
@SpringBootTest(classes = CloudAiAutoConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "cloud-ai.llm.default-provider=openai",
                "cloud-ai.llm.providers.openai.base-url=https://llm.example.com",
                "cloud-ai.llm.providers.openai.api-key=test-key",
                "cloud-ai.llm.providers.openai.model=test-model",
                "cloud-ai.skills.work-dir=build/no-skills-here",
                "cloud-ai.context.work-dir=."
        })
class CloudAiAutoConfigurationTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentLoop agentLoop;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    @DisplayName("AgentService/AgentLoop/ToolRegistry 装配成功，内置工具已注册")
    void coreBeansPresent() {
        assertNotNull(agentService);
        assertNotNull(agentLoop);
        assertNotNull(toolRegistry);
        assertTrue(toolRegistry.listDefinitions().stream()
                .anyMatch(d -> d.name().equals("file_read")));
    }

    @Test
    @DisplayName("工作目录无技能时不注册 load_skill")
    void loadSkillNotRegisteredWithoutSkills() {
        assertFalse(toolRegistry.listDefinitions().stream()
                .anyMatch(d -> d.name().equals("load_skill")));
    }
}
