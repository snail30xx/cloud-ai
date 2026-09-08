package com.cloudai.llm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * OpenAI {@code GET /v1/models} 响应体。
 *
 * <pre>
 * {
 *   "object": "list",
 *   "data": [
 *     { "id": "gpt-4o", "object": "model", "created": 1686935002, "owned_by": "openai" }
 *   ]
 * }
 * </pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public record OpenAiModelListResponse(
        @Nullable String object,
        @Nullable List<OpenAiModelEntry> data) {

    /**
     * 单个模型条目。
     */
    public record OpenAiModelEntry(
            @Nullable String id,
            @Nullable String object,
            long created,
            @Nullable @JsonProperty("owned_by") String ownedBy) {
    }
}