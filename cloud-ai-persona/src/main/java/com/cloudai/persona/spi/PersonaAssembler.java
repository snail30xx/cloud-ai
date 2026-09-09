package com.cloudai.persona.spi;

import com.cloudai.core.model.ToolDefinition;
import com.cloudai.persona.model.Persona;

import java.util.List;

/**
 * 人格组装器 — 将人格定义组装为最终的 system prompt。
 *
 * <p>组装逻辑包括：拼接基础提示词、角色描述、行为准则、语气风格和约束，
 * 并可选地附加可用工具描述。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface PersonaAssembler {

    /**
     * 组装最终 system prompt。
     *
     * @param persona 人格定义
     * @param tools   可用工具列表，可为空
     * @return 组装后的 system prompt 字符串
     */
    String assemble(Persona persona, List<ToolDefinition> tools);
}
