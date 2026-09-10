package com.cloudai.spring;

import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.server.service.AgentService;
import com.cloudai.spring.config.CloudAiAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** skills 模块禁用时，装配链路保持可用但不注册技能工具。 */
@DisplayName("skills 禁用时：装配链路可用")
@SpringBootTest(classes = CloudAiAutoConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "cloud-ai.llm.default-provider=openai",
                "cloud-ai.llm.providers.openai.base-url=https://llm.example.com",
                "cloud-ai.llm.providers.openai.api-key=test-key",
                "cloud-ai.llm.providers.openai.model=test-model",
                "cloud-ai.skills.enabled=false",
                "cloud-ai.context.work-dir=."
        })
class SkillsDisabledAutoConfigurationTest {

    @Autowired
    private AgentService agentService;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    @DisplayName("AgentService 正常装配，无 load_skill 工具")
    void agentServicePresentWithoutSkillTool() {
        assertNotNull(agentService);
        assertFalse(toolRegistry.listDefinitions().stream()
                .anyMatch(d -> d.name().equals("load_skill")));
    }
}
