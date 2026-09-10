package com.cloudai.llm.client.support;

import com.cloudai.core.chat.ChatRequest;
import com.cloudai.core.chat.ChatResponse;
import com.cloudai.core.chat.Message;
import com.cloudai.core.chat.ModelOptions;
import com.cloudai.core.tool.ToolDefinition;
import com.cloudai.llm.advisor.Advisor;
import com.cloudai.llm.client.spec.CallResponseSpec;
import com.cloudai.llm.client.spec.ChatClientRequestSpec;
import com.cloudai.llm.client.spec.StreamResponseSpec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ChatClientRequestSpec} 的默认实现。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class DefaultChatClientRequestSpec implements ChatClientRequestSpec {

    private final ModelRouterAccessor router;
    private final List<Advisor> advisors;
    private final List<Message> messages = new ArrayList<>();
    private final List<ToolDefinition> tools = new ArrayList<>();
    private ModelOptions options = ModelOptions.DEFAULT;
    @Nullable
    private String providerName;

    public DefaultChatClientRequestSpec(ModelRouterAccessor router, List<Advisor> advisors) {
        this.router = router;
        this.advisors = new ArrayList<>(advisors);
    }

    public DefaultChatClientRequestSpec(ModelRouterAccessor router, List<Advisor> advisors, ChatRequest request) {
        this(router, advisors);
        if (request.messages() != null) {
            this.messages.addAll(request.messages());
        }
        if (request.tools() != null) {
            this.tools.addAll(request.tools());
        }
        if (request.options() != null) {
            this.options = request.options();
        }
    }

    @Override
    public ChatClientRequestSpec user(String content) {
        messages.add(Message.user(content));
        return this;
    }

    @Override
    public ChatClientRequestSpec system(String content) {
        messages.add(Message.system(content));
        return this;
    }

    @Override
    public ChatClientRequestSpec assistant(String content) {
        messages.add(Message.assistant(content));
        return this;
    }

    @Override
    public ChatClientRequestSpec messages(@Nullable Message... msgs) {
        if (msgs != null) {
            messages.addAll(List.of(msgs));
        }
        return this;
    }

    @Override
    public ChatClientRequestSpec tools(@Nullable ToolDefinition... toolDefs) {
        if (toolDefs != null) {
            tools.addAll(List.of(toolDefs));
        }
        return this;
    }

    @Override
    public ChatClientRequestSpec options(@Nullable ModelOptions opts) {
        this.options = opts != null ? opts : ModelOptions.DEFAULT;
        return this;
    }

    @Override
    public ChatClientRequestSpec provider(String name) {
        this.providerName = name;
        return this;
    }

    @Override
    public ChatClientRequestSpec advisors(@Nullable Advisor... advisorArray) {
        if (advisorArray != null) {
            advisors.addAll(List.of(advisorArray));
        }
        return this;
    }

    @Override
    public CallResponseSpec call() {
        var request = applyAdvisors(buildRequest());
        var response = providerName != null
                ? router.chat(providerName, request)
                : router.chatDefault(request);
        return new DefaultCallResponseSpec(response);
    }

    @Override
    public StreamResponseSpec stream() {
        var request = applyAdvisors(buildRequest());
        var flux = providerName != null
                ? router.stream(providerName, request)
                : router.streamDefault(request);
        return new DefaultStreamResponseSpec(flux);
    }

    private ChatRequest buildRequest() {
        return new ChatRequest(List.copyOf(messages),
                tools.isEmpty() ? null : List.copyOf(tools),
                options);
    }

    private ChatRequest applyAdvisors(ChatRequest request) {
        if (advisors.isEmpty()) {
            return request;
        }
        return buildChain(advisors, 0).next(request);
    }

    private static Advisor.AdvisorChain buildChain(List<Advisor> advisorList, int index) {
        return req -> {
            if (index >= advisorList.size()) {
                return req;
            }
            return advisorList.get(index).advise(req, buildChain(advisorList, index + 1));
        };
    }
}