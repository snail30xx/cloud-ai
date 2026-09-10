package com.cloudai.spring;

import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.server.service.AgentService;
import com.cloudai.spring.config.CloudAiAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工作目录下有技能时，load_skill 注册进 ToolRegistry。 */
@DisplayName("有技能时：load_skill 注册")
@SpringBootTest(classes = CloudAiAutoConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SkillsPresentAutoConfigurationTest {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws IOException {
        var tempDir = Files.createTempDirectory("cloud-ai-skills-test");
        var skillDir = Files.createDirectories(tempDir.resolve(".agents/skills/demo"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: demo-skill
                description: A demo skill for testing
                ---

                Demo skill body.
                """);
        registry.add("cloud-ai.llm.default-provider", () -> "openai");
        registry.add("cloud-ai.llm.providers.openai.base-url", () -> "https://llm.example.com");
        registry.add("cloud-ai.llm.providers.openai.api-key", () -> "test-key");
        registry.add("cloud-ai.llm.providers.openai.model", () -> "test-model");
        registry.add("cloud-ai.skills.work-dir", () -> tempDir.toString());
        registry.add("cloud-ai.context.work-dir", () -> tempDir.toString());
    }

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private AgentService agentService;

    @Test
    @DisplayName("load_skill 工具可用且 AgentService 装配成功")
    void loadSkillRegistered() {
        assertTrue(toolRegistry.listDefinitions().stream()
                .anyMatch(d -> d.name().equals("load_skill")));
        assertNotNull(agentService);
    }
}
