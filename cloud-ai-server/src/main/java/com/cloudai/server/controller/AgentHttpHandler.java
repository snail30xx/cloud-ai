package com.cloudai.server.controller;

import com.cloudai.llm.exception.LlmAuthException;
import com.cloudai.llm.exception.LlmClientException;
import com.cloudai.llm.exception.LlmException;
import com.cloudai.llm.exception.LlmRateLimitException;
import com.cloudai.llm.exception.LlmServerException;
import com.cloudai.llm.exception.LlmTimeoutException;
import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.server.dto.AgentRunRequest;
import com.cloudai.server.dto.AgentRunResponse;
import com.cloudai.server.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent HTTP 处理器 — 基于 JDK HttpServer，替代 Spring MVC 控制器。
 *
 * <p>端点：
 * <ul>
 *   <li>POST /api/agent/run — 同步运行 Agent</li>
 *   <li>GET  /api/agent/status/{traceId} — 查询运行状态</li>
 *   <li>POST /api/agent/interrupt/{traceId} — 中断运行中的会话</li>
 * </ul>
 *
 * <p>内嵌 API Key 校验和全局异常处理，替代 Spring Interceptor 和 RestControllerAdvice。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class AgentHttpHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(AgentHttpHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String PATH_PREFIX = "/api/agent";
    private static final String RUN_PATH = "/api/agent/run";
    private static final String STATUS_PREFIX = "/api/agent/status/";
    private static final String INTERRUPT_PREFIX = "/api/agent/interrupt/";

    private final AgentService agentService;
    @Nullable
    private final String apiKey;

    public AgentHttpHandler(AgentService agentService, @Nullable String apiKey) {
        this.agentService = agentService;
        this.apiKey = apiKey;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!checkApiKey(exchange)) {
                sendJson(exchange, 401, Map.of("code", 401, "message", "Invalid or missing API key"));
                return;
            }

            var method = exchange.getRequestMethod();
            var path = exchange.getRequestURI().getPath();

            if ("POST".equals(method) && RUN_PATH.equals(path)) {
                handleRun(exchange);
            } else if ("GET".equals(method) && path.startsWith(STATUS_PREFIX)) {
                handleStatus(exchange, path.substring(STATUS_PREFIX.length()));
            } else if ("POST".equals(method) && path.startsWith(INTERRUPT_PREFIX)) {
                handleInterrupt(exchange, path.substring(INTERRUPT_PREFIX.length()));
            } else {
                sendJson(exchange, 404, Map.of("code", 404, "message", "Not found"));
            }
        } catch (Exception e) {
            handleException(exchange, e);
        } finally {
            exchange.close();
        }
    }

    private boolean checkApiKey(HttpExchange exchange) {
        if (apiKey == null || apiKey.isBlank()) {
            return true;
        }
        var requestKey = exchange.getRequestHeaders().getFirst(HEADER_API_KEY);
        return requestKey != null && requestKey.equals(apiKey);
    }

    // ---- 端点处理 ----

    private void handleRun(HttpExchange exchange) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        AgentRunRequest request;
        try {
            request = MAPPER.readValue(body, AgentRunRequest.class);
        } catch (Exception e) {
            sendJson(exchange, 400, Map.of("code", 400, "message", "Invalid request body: " + e.getMessage()));
            return;
        }

        var response = agentService.run(
                request.prompt(),
                request.provider(),
                request.maxTurns(),
                request.timeout(),
                request.traceId());
        sendJson(exchange, 200, toDto(response));
    }

    private void handleStatus(HttpExchange exchange, String traceId) throws IOException {
        var running = agentService.isRunning(traceId);
        sendJson(exchange, 200, Map.of("traceId", traceId, "running", running));
    }

    private void handleInterrupt(HttpExchange exchange, String traceId) throws IOException {
        agentService.interrupt(traceId);
        sendJson(exchange, 200, Map.of("traceId", traceId, "interrupted", true));
    }

    // ---- 异常处理（替代 GlobalExceptionHandler） ----

    private void handleException(HttpExchange exchange, Exception e) {
        int status;
        String message;

        if (e instanceof LlmAuthException) {
            status = 401;
            message = "Authentication failed";
            log.warn("LLM auth error: {}", e.getMessage());
        } else if (e instanceof LlmRateLimitException) {
            status = 429;
            message = "Rate limited by provider";
            log.warn("LLM rate limited: {}", e.getMessage());
        } else if (e instanceof LlmTimeoutException) {
            status = 504;
            message = "Request timed out";
            log.warn("LLM timeout: {}", e.getMessage());
        } else if (e instanceof LlmServerException) {
            status = 502;
            message = "Provider server error";
            log.error("LLM server error: {}", e.getMessage(), e);
        } else if (e instanceof LlmClientException) {
            status = 400;
            message = "Invalid request to provider";
            log.warn("LLM client error: {}", e.getMessage());
        } else if (e instanceof LlmException) {
            status = 500;
            message = "LLM error";
            log.error("Unhandled LLM error: {}", e.getMessage(), e);
        } else if (e instanceof IllegalArgumentException) {
            status = 400;
            message = e.getMessage();
        } else {
            status = 500;
            message = "Internal error";
            log.error("Unhandled exception", e);
        }

        try {
            sendJson(exchange, status, errorBody(status, message));
        } catch (IOException ioe) {
            log.error("Failed to send error response", ioe);
        }
    }

    // ---- 辅助方法 ----

    private static AgentRunResponse toDto(AgentResponse response) {
        return new AgentRunResponse(
                response.traceId(),
                response.content(),
                response.finishStatus().name(),
                response.turnsExecuted(),
                response.toolCallsExecuted(),
                response.error());
    }

    private static Map<String, Object> errorBody(int code, String message) {
        return Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "code", code,
                "message", message);
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
        var json = MAPPER.writeValueAsString(body);
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}