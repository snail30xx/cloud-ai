package com.cloudai.server;

import com.cloudai.context.config.ContextProperties;
import com.cloudai.context.ContextProvider;
import com.cloudai.execution.config.ExecutionProperties;
import com.cloudai.execution.ToolExecutionService;
import com.cloudai.execution.registry.ToolRegistry;
import com.cloudai.llm.config.LlmProperties;
import com.cloudai.llm.config.ProviderProperties;
import com.cloudai.llm.ModelRouter;
import com.cloudai.memory.MemoryRetriever;
import com.cloudai.memory.MemoryStore;
import com.cloudai.persona.PersonaAssembler;
import com.cloudai.persona.PersonaProvider;
import com.cloudai.runtime.config.RuntimeProperties;
import com.cloudai.runtime.AgentLoop;
import com.cloudai.security.config.SecurityProperties;
import com.cloudai.security.SecurityInterceptor;
import com.cloudai.server.controller.AgentHttpHandler;
import com.cloudai.server.service.AgentService;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Cloud AI 启动入口 — 替代 Spring Boot 引导。
 *
 * <p>手动加载配置、装配各模块 Bean、启动 JDK 内置 HttpServer。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class CloudAiApplication {
    private static final Logger log = LoggerFactory.getLogger(CloudAiApplication.class);

    private CloudAiApplication() {}

    public static void main(String[] args) throws Exception {
        var props = loadProperties();

        int port = Integer.parseInt(props.getProperty("server.port", "8080"));
        String apiKey = props.getProperty("cloud-ai.server.api-key", "");

        // ---- 装配各模块 ----
        // 此处使用各模块的工厂类手动装配，与 CloudAi facade 的 Builder 模式一致
        // 实际部署时可通过 CloudAi.builder() 或直接调用工厂方法装配

        log.info("Starting Cloud AI HTTP server on port {}", port);
        var server = HttpServer.create(new InetSocketAddress(port), 0);

        // AgentService 由外部装配后传入
        // 这里仅设置路由和启动
        // 实际使用时，由调用方完成 AgentService 装配后创建 AgentHttpHandler
        // server.createContext("/api/agent/", new AgentHttpHandler(agentService, apiKey));

        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        log.info("Cloud AI HTTP server started on port {}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down Cloud AI HTTP server");
            server.stop(2);
        }));
    }

    /** 从 classpath 或文件系统加载 application.properties。 */
    static Properties loadProperties() {
        var props = new Properties();
        try (InputStream is = CloudAiApplication.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
                log.info("Loaded application.properties from classpath");
            } else {
                log.warn("application.properties not found on classpath, using defaults");
            }
        } catch (Exception e) {
            log.warn("Failed to load application.properties: {}", e.getMessage());
        }
        return props;
    }
}