package com.cloudai.persona.spi;

import com.cloudai.persona.model.Persona;

import java.util.Optional;

/**
 * 人格提供者 — 按 ID 解析人格定义。
 *
 * <p>实现可以从配置文件、数据库或外部系统加载人格定义。
 * 支持多 Agent 不同人格。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface PersonaProvider {

    /**
     * 按 ID 查找人格。
     *
     * @param personaId 人格标识
     * @return 人格定义，不存在时返回 empty
     */
    Optional<Persona> findById(String personaId);

    /**
     * 返回默认人格。
     *
     * @return 默认人格定义
     */
    Persona defaultPersona();
}
