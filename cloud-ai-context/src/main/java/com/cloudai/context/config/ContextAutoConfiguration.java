package com.cloudai.context.config;

import com.cloudai.context.impl.DefaultEnvironmentProvider;
import com.cloudai.context.impl.FilesystemProjectContextProvider;
import com.cloudai.context.spi.ContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上下文模块工厂 — 替代 Spring 自动装配。
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class ContextAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(ContextAutoConfiguration.class);

    private ContextAutoConfiguration() {}

    public static ContextProvider environmentProvider(ContextProperties props) {
        log.info("Creating DefaultEnvironmentProvider: workDir={}", props.getWorkDir());
        return new DefaultEnvironmentProvider(props.getWorkDir());
    }

    public static ContextProvider projectContextProvider(ContextProperties props) {
        if (!props.isProjectContextEnabled()) {
            return () -> com.cloudai.context.impl.PromptSectionImpl.empty("ProjectContext");
        }
        log.info("Creating FilesystemProjectContextProvider: workDir={}", props.getWorkDir());
        return new FilesystemProjectContextProvider(props.getWorkDir());
    }
}