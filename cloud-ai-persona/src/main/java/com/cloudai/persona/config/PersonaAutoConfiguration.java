package com.cloudai.persona.config;

import com.cloudai.persona.advisor.PersonaAdvisor;
import com.cloudai.persona.impl.DefaultPersonaAssembler;
import com.cloudai.persona.impl.DefaultPersonaProvider;
import com.cloudai.persona.spi.PersonaAssembler;
import com.cloudai.persona.spi.PersonaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 人格模块自动装配。
 *
 * <p>通过 {@code cloud-ai.persona.enabled} 控制（默认 true）。
 * 两个核心 Bean 均为 {@link ConditionalOnMissingBean}，允许外部替换。
 * {@link PersonaAdvisor} 可选装配，仅在设置 {@code cloud-ai.persona.advisor.enabled=true} 时创建。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties(PersonaProperties.class)
@ConditionalOnProperty(name = "cloud-ai.persona.enabled", havingValue = "true", matchIfMissing = true)
public class PersonaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PersonaAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PersonaProvider personaProvider(PersonaProperties properties) {
        log.info("Creating DefaultPersonaProvider");
        return new DefaultPersonaProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonaAssembler personaAssembler() {
        log.info("Creating DefaultPersonaAssembler");
        return new DefaultPersonaAssembler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "cloud-ai.persona.advisor.enabled", havingValue = "true")
    public PersonaAdvisor personaAdvisor(PersonaProvider provider, PersonaAssembler assembler) {
        log.info("Creating PersonaAdvisor");
        return new PersonaAdvisor(provider, assembler);
    }
}
