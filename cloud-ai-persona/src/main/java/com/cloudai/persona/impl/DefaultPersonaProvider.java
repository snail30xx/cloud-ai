package com.cloudai.persona.impl;

import com.cloudai.persona.config.PersonaProperties;
import com.cloudai.persona.config.PersonaProperties.PersonaConfig;
import com.cloudai.persona.model.Persona;
import com.cloudai.persona.spi.PersonaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认人格提供者 — 从 {@link PersonaProperties} 配置加载人格定义。
 *
 * <p>如果配置中未定义任何人格，则使用内置的默认人格。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultPersonaProvider implements PersonaProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersonaProvider.class);

    private final Map<String, Persona> personas;
    private final String defaultPersonaId;

    public DefaultPersonaProvider(PersonaProperties properties) {
        this.defaultPersonaId = properties.defaultPersonaId();
        var loaded = loadPersonas(properties);
        if (loaded.isEmpty()) {
            var builtin = builtinDefault();
            loaded = Map.of(builtin.id(), builtin);
            log.info("No personas configured, using built-in default persona: '{}'", builtin.id());
        } else {
            log.info("Loaded {} persona(s), default: '{}'", loaded.size(), defaultPersonaId);
        }
        this.personas = loaded;
    }

    @Override
    public Optional<Persona> findById(String personaId) {
        if (personaId == null || personaId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(personas.get(personaId));
    }

    @Override
    public Persona defaultPersona() {
        return personas.getOrDefault(defaultPersonaId, personas.values().iterator().next());
    }

    private Map<String, Persona> loadPersonas(PersonaProperties properties) {
        var builder = new HashMap<String, Persona>();
        for (var entry : properties.personas().entrySet()) {
            var id = entry.getKey();
            var config = entry.getValue();
            var name = config.name() != null && !config.name().isBlank() ? config.name() : id;
            var systemPrompt = config.systemPrompt() != null && !config.systemPrompt().isBlank()
                    ? config.systemPrompt()
                    : "You are a helpful AI assistant.";
            var persona = new Persona(id, name, config.role(), systemPrompt,
                    config.guidelines(), config.toneStyle(), config.constraints());
            builder.put(id, persona);
        }
        return Map.copyOf(builder);
    }

    /** 内置默认人格，在配置为空时使用。 */
    public static Persona builtinDefault() {
        return new Persona(
                "default",
                "Cloud AI Assistant",
                "AI assistant",
                "You are a helpful AI assistant. You assist users with tasks by using available tools when needed.",
                List.of(
                        "Always be accurate and honest",
                        "Ask for clarification when the request is ambiguous",
                        "Explain your reasoning when making decisions"),
                "professional and concise",
                List.of(
                        "Never share sensitive data or credentials",
                        "Do not execute destructive operations without confirmation"));
    }
}

