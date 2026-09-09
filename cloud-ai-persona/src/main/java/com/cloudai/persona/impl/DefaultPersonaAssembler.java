package com.cloudai.persona.impl;

import com.cloudai.core.model.ToolDefinition;
import com.cloudai.persona.model.Persona;
import com.cloudai.persona.spi.PersonaAssembler;

import java.util.List;

/**
 * 默认人格组装器 — 将人格定义拼装为结构化的 system prompt。
 *
 * <p>组装格式：</p>
 * <pre>{@code
 * {systemPrompt}
 *
 * You are a {role}.
 *
 * Guidelines:
 * - {guideline1}
 * - {guideline2}
 *
 * Tone: {toneStyle}
 *
 * Constraints:
 * - {constraint1}
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultPersonaAssembler implements PersonaAssembler {

    private static final String SEP = "\n\n";

    @Override
    public String assemble(Persona persona, List<ToolDefinition> tools) {
        if (persona == null) {
            throw new IllegalArgumentException("persona must not be null");
        }
        var sb = new StringBuilder();
        sb.append(persona.systemPrompt());

        if (persona.role() != null && !persona.role().isBlank()) {
            sb.append(SEP).append("You are a ").append(persona.role()).append(".");
        }

        if (!persona.guidelines().isEmpty()) {
            sb.append(SEP).append("Guidelines:");
            for (var g : persona.guidelines()) {
                sb.append("\n- ").append(g);
            }
        }

        if (persona.toneStyle() != null && !persona.toneStyle().isBlank()) {
            sb.append(SEP).append("Tone: ").append(persona.toneStyle()).append(".");
        }

        if (!persona.constraints().isEmpty()) {
            sb.append(SEP).append("Constraints:");
            for (var c : persona.constraints()) {
                sb.append("\n- ").append(c);
            }
        }

        if (tools != null && !tools.isEmpty()) {
            sb.append(SEP).append("Available tools:");
            for (var tool : tools) {
                sb.append("\n- ").append(tool.name());
                if (tool.description() != null && !tool.description().isBlank()) {
                    sb.append(": ").append(tool.description());
                }
            }
        }

        return sb.toString();
    }
}
