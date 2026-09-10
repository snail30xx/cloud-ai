package com.cloudai.security.audit;

import com.cloudai.security.audit.AuditEvent;
import com.cloudai.security.audit.AuditLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SLF4J 审计日志 — 结构化 JSON 输出。
 *
 * <p>日志级别：
 * <ul>
 *   <li>access — DEBUG</li>
 *   <li>decision — INFO（拒绝时 WARN）</li>
 *   <li>execution — INFO（失败时 ERROR）</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class Slf4jAuditLogger implements AuditLogger {
    private static final Logger log = LoggerFactory.getLogger(Slf4jAuditLogger.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void logAccess(AuditEvent event) {
        log.debug("AUDIT_ACCESS {}", toJson(event));
    }

    @Override
    public void logDecision(AuditEvent event) {
        var json = toJson(event);
        if (isDenied(event.result())) {
            log.warn("AUDIT_DECISION {}", json);
        } else {
            log.info("AUDIT_DECISION {}", json);
        }
    }

    @Override
    public void logExecution(AuditEvent event) {
        var json = toJson(event);
        if (isFailed(event.result())) {
            log.error("AUDIT_EXECUTION {}", json);
        } else {
            log.info("AUDIT_EXECUTION {}", json);
        }
    }

    private boolean isDenied(String result) {
        if (result == null) return false;
        var upper = result.toUpperCase();
        return upper.startsWith(AuditEvent.RESULT_DENIED) || upper.startsWith("REJECT");
    }

    private boolean isFailed(String result) {
        if (result == null) return false;
        var upper = result.toUpperCase();
        return upper.startsWith(AuditEvent.RESULT_FAILED) || upper.startsWith(AuditEvent.RESULT_ERROR);
    }

    private String toJson(AuditEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("agentId", event.agentId());
        map.put("operation", event.operation());
        map.put("target", event.target());
        map.put("result", event.result());
        map.put("timestamp", event.timestamp().toString());
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            map.put("metadata", event.metadata());
        }
        try {
            return mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit event to JSON, falling back to Map.toString(): {}", e.getMessage());
            return map.toString();
        }
    }
}
