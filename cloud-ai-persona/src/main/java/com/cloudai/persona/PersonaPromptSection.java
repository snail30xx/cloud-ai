package com.cloudai.persona;

import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.core.prompt.PromptSection;
import com.cloudai.persona.Persona;
import com.cloudai.persona.PersonaAssembler;

import java.util.List;

/**
 * 人格提示词段落 — 将 Persona 组装为 order=10 的静态段落。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class PersonaPromptSection implements PromptSection {

    private final Persona persona;
    private final PersonaAssembler assembler;
    private final List<ToolDefinition> tools;

    public PersonaPromptSection(Persona persona, PersonaAssembler assembler, List<ToolDefinition> tools) {
        this.persona = persona;
        this.assembler = assembler;
        this.tools = tools != null ? List.copyOf(tools) : List.of();
    }

    @Override
    public String name() {
        return "Persona";
    }

    @Override
    public String content() {
        return assembler.assemble(persona, tools);
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public boolean cacheable() {
        return true;
    }
}
