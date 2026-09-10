package com.cloudai.skills;

import com.cloudai.core.tool.ToolCall;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.execution.ToolResult;
import com.cloudai.execution.ToolExecutor;
import com.cloudai.skills.SkillLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 技能加载执行器 — LLM 通过 load_skill 工具按需加载技能完整内容。
 *
 * <p>参数 JSON：{@code {"name": "code-review"}}</p>
 *
 * <p>这是渐进加载的核心：LLM 先看到技能菜单（name + description），
 * 需要使用时调用此工具获取完整指令。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LoadSkillExecutor implements ToolExecutor {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final SkillLoader loader;

    public LoadSkillExecutor(SkillLoader loader) {
        this.loader = loader;
    }

    public static ToolDefinition definition() {
        return new ToolDefinition(
                "load_skill",
                "Load the full instructions of a skill by name. Use when you need to follow a skill's detailed steps.",
                java.util.Map.of("type", "object"));
    }

    @Override
    public ToolResult execute(ToolCall toolCall) {
        try {
            var name = extractName(toolCall);
            if (name == null || name.isBlank()) {
                return ToolResult.failure(toolCall.id(), "Missing 'name' parameter");
            }
            var content = loader.load(name);
            if (content == null) {
                return ToolResult.failure(toolCall.id(), "Skill not found: " + name);
            }
            return ToolResult.success(toolCall.id(), content);
        } catch (Exception e) {
            return ToolResult.failure(toolCall.id(), "Failed to load skill: " + e.getMessage());
        }
    }

    private String extractName(ToolCall toolCall) throws Exception {
        if (toolCall.arguments() == null || toolCall.arguments().isBlank()) {
            return null;
        }
        JsonNode node = mapper.readTree(toolCall.arguments());
        var nameNode = node.get("name");
        return nameNode != null && nameNode.isTextual() ? nameNode.asText() : null;
    }
}
