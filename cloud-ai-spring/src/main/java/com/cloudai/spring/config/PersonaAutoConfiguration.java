package com.cloudai.spring.config;

import com.cloudai.persona.config.PersonaProperties;
import com.cloudai.persona.PersonaAssembler;
import com.cloudai.persona.PersonaProvider;
import com.cloudai.spring.properties.CloudAiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
@ConditionalOnProperty(name = "cloud-ai.persona.enabled", havingValue = "true", matchIfMissing = true)
public class PersonaAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public PersonaProvider personaProvider(CloudAiProperties props) {
        var springPersona = props.persona() != null ? props.persona() : new CloudAiProperties.Persona(null, null);
        var personas = new HashMap<String, PersonaProperties.PersonaConfig>();
        if (springPersona.personas() != null) {
            for (var entry : springPersona.personas().entrySet()) {
                var p = entry.getValue();
                personas.put(entry.getKey(), new PersonaProperties.PersonaConfig(
                        p.name(), p.role(), p.systemPrompt(),
                        p.guidelines(), p.toneStyle(), p.constraints()));
            }
        }
        var personaProps = new PersonaProperties(springPersona.defaultPersonaId(), personas);
        return com.cloudai.persona.config.PersonaAutoConfiguration.personaProvider(personaProps);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonaAssembler personaAssembler() {
        return com.cloudai.persona.config.PersonaAutoConfiguration.personaAssembler();
    }
}