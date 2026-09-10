package com.cloudai.llm.advisor;

import com.cloudai.core.chat.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志 Advisor — 记录每次 LLM 调用的请求信息。
 *
 * @author cloud-ai
 * @since 1.0
 */
public class LoggingAdvisor implements Advisor {

    private static final Logger log = LoggerFactory.getLogger(LoggingAdvisor.class);

    @Override
    public ChatRequest advise(ChatRequest request, AdvisorChain chain) {
        log.debug("LLM request: {} messages, {} tools, options={}",
                request.messages() != null ? request.messages().size() : 0,
                request.tools() != null ? request.tools().size() : 0,
                request.options());
        return chain.next(request);
    }
}