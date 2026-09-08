package com.cloudai.llm.client.spec;

import com.cloudai.core.model.Message;
import com.cloudai.core.model.ModelOptions;
import com.cloudai.core.model.ToolDefinition;
import com.cloudai.llm.advisor.Advisor;
import org.jspecify.annotations.Nullable;

/**
 * ChatClient 请求规格 — 流式构建器接口。
 *
 * <p>支持链式调用配置消息、工具、参数，然后调用 {@link #call()} 或 {@link #stream()}。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface ChatClientRequestSpec {

    /** 添加用户消息 */
    ChatClientRequestSpec user(String content);

    /** 添加系统消息 */
    ChatClientRequestSpec system(String content);

    /** 添加助手消息 */
    ChatClientRequestSpec assistant(String content);

    /** 添加多条消息 */
    ChatClientRequestSpec messages(@Nullable Message... messages);

    /** 注册工具定义 */
    ChatClientRequestSpec tools(@Nullable ToolDefinition... tools);

    /** 设置模型参数 */
    ChatClientRequestSpec options(@Nullable ModelOptions options);

    /** 指定 provider 名称（路由 key） */
    ChatClientRequestSpec provider(String name);

    /** 添加 advisors（请求拦截器） */
    ChatClientRequestSpec advisors(@Nullable Advisor... advisors);

    /** 执行同步调用 */
    CallResponseSpec call();

    /** 执行流式调用 */
    StreamResponseSpec stream();
}