package com.cloudai.server.controller;

import com.cloudai.runtime.model.AgentResponse;
import com.cloudai.server.dto.AgentRunRequest;
import com.cloudai.server.dto.AgentRunResponse;
import com.cloudai.server.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Agent HTTP API — 对外暴露 Agent 运行、状态查询和中断接口。
 *
 * <p>端点：
 * <ul>
 *   <li>{@code POST /api/agent/run} — 同步运行 Agent</li>
 *   <li>{@code GET /api/agent/status/{traceId}} — 查询运行状态</li>
 *   <li>{@code POST /api/agent/interrupt/{traceId}} — 中断运行中的会话</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 同步运行 Agent 循环。
     */
    @PostMapping("/run")
    public ResponseEntity<AgentRunResponse> run(@RequestBody AgentRunRequest request) {
        var response = agentService.run(
                request.prompt(),
                request.provider(),
                request.maxTurns(),
                request.timeout(),
                request.traceId());
        return ResponseEntity.ok(toDto(response));
    }

    /**
     * 查询指定会话的运行状态。
     */
    @GetMapping("/status/{traceId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String traceId) {
        var running = agentService.isRunning(traceId);
        return ResponseEntity.ok(Map.of("traceId", traceId, "running", running));
    }

    /**
     * 中断运行中的 Agent 会话。
     */
    @PostMapping("/interrupt/{traceId}")
    public ResponseEntity<Map<String, Object>> interrupt(@PathVariable String traceId) {
        agentService.interrupt(traceId);
        return ResponseEntity.ok(Map.of("traceId", traceId, "interrupted", true));
    }

    private static AgentRunResponse toDto(AgentResponse response) {
        return new AgentRunResponse(
                response.traceId(),
                response.content(),
                response.finishStatus().name(),
                response.turnsExecuted(),
                response.toolCallsExecuted(),
                response.error());
    }
}
