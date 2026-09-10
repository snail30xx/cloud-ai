package com.cloudai.persona.config;

import com.cloudai.persona.advisor.PersonaAdvisor;
import com.cloudai.persona.impl.DefaultPersonaAssembler;
import com.cloudai.persona.impl.DefaultPersonaProvider;
import com.cloudai.persona.spi.PersonaAssembler;
import com.cloudai.persona.spi.PersonaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 人格模块工厂 — 替代 Spring 自动装配。
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class PersonaAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(PersonaAutoConfiguration.class);

    private PersonaAutoConfiguration() {}

    public static PersonaProvider personaProvider(PersonaProperties properties) {
        log.info("Creating DefaultPersonaProvider");
        return new DefaultPersonaProvider(properties);
    }

    public static PersonaAssembler personaAssembler() {
        log.info("Creating DefaultPersonaAssembler");
        return new DefaultPersonaAssembler();
    }

    public static PersonaAdvisor personaAdvisor(PersonaProvider provider, PersonaAssembler assembler) {
        log.info("Creating PersonaAdvisor");
        return new PersonaAdvisor(provider, assembler);
    }
}