package com.cloudai.persona.advisor;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.Message;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.llm.advisor.Advisor;
import com.cloudai.persona.Persona;
import com.cloudai.persona.PersonaAssembler;
import com.cloudai.persona.PersonaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 人格 Advisor — 在 LLM 调用前注入人格 system prompt。
 *
 * <p>从 {@link PersonaProvider} 解析默认人格，经 {@link PersonaAssembler}
 * 组装为 system prompt，注入到消息列表头部。如果消息列表中已存在
 * system 消息，则将人格 prompt 追加到其后。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class PersonaAdvisor implements Advisor {

    private static final Logger log = LoggerFactory.getLogger(PersonaAdvisor.class);

    private final PersonaProvider provider;
    private final PersonaAssembler assembler;

    public PersonaAdvisor(PersonaProvider provider, PersonaAssembler assembler) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (assembler == null) {
            throw new IllegalArgumentException("assembler must not be null");
        }
        this.provider = provider;
        this.assembler = assembler;
    }

    @Override
    public ChatRequest advise(ChatRequest request, AdvisorChain chain) {
        var persona = provider.defaultPersona();
        var systemPrompt = assembler.assemble(persona, request.tools());

        var augmented = new ArrayList<Message>(request.messages().size() + 1);

        boolean injected = false;
        for (var msg : request.messages()) {
            if (!injected && msg.isSystem()) {
                // 在第一个 system 消息后注入人格 prompt
                augmented.add(msg);
                augmented.add(Message.system(systemPrompt));
                injected = true;
            } else {
                augmented.add(msg);
            }
        }

        if (!injected) {
            // 没有 system 消息，在头部注入
            augmented.add(0, Message.system(systemPrompt));
        }

        log.debug("PersonaAdvisor injected persona '{}' ({} chars)", persona.id(), systemPrompt.length());

        var newRequest = new ChatRequest(List.copyOf(augmented), request.tools(), request.options());
        return chain.next(newRequest);
    }
}
