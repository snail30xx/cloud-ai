package com.cloudai.core.chat;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型选项。
 *
 * @param model       模型名称
 * @param temperature 温度参数
 * @param maxTokens   最大输出 Token
 * @param topP        Top-P 采样
 * @param stop        停止词列表
 * @param extraBody   额外请求参数（透传给 provider，用于非标准参数）
  * @author cloud-ai
 * @since 1.0
*/
public record ModelOptions(@Nullable String model, @Nullable Double temperature,
                           @Nullable Integer maxTokens, @Nullable Double topP,
                           @Nullable List<String> stop, @Nullable Map<String, Object> extraBody) {

    public static final ModelOptions DEFAULT = new ModelOptions(null, null, null, null, null, Map.of());

    public ModelOptions {
        stop = stop != null ? List.copyOf(stop) : List.of();
        extraBody = extraBody != null ? Map.copyOf(extraBody) : Map.of();
    }

    /** 向后兼容的构造器（无 extraBody）。  * @author cloud-ai
 * @since 1.0
*/
    public ModelOptions(String model, Double temperature, Integer maxTokens, Double topP, List<String> stop) {
        this(model, temperature, maxTokens, topP, stop, Map.of());
    }

    /** 创建 Builder。  * @author cloud-ai
 * @since 1.0
*/
    public static Builder builder() {
        return new Builder();
    }

    /** 基于当前值创建预填充的 Builder。  * @author cloud-ai
 * @since 1.0
*/
    public Builder mutate() {
        return new Builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .topP(topP)
                .stop(stop)
                .extraBody(new HashMap<>(extraBody));
    }

    /** 添加单个 extraBody 键值对。  * @author cloud-ai
 * @since 1.0
*/
    public ModelOptions withExtraBody(String key, Object value) {
        var map = new HashMap<>(extraBody);
        map.put(key, value);
        return new ModelOptions(model, temperature, maxTokens, topP, stop, Map.copyOf(map));
    }

    /** 便捷：仅修改 model。  * @author cloud-ai
 * @since 1.0
*/
    public ModelOptions withModel(String model) {
        return new ModelOptions(model, temperature, maxTokens, topP, stop, extraBody);
    }

    /** ModelOptions Builder。  * @author cloud-ai
 * @since 1.0
*/
    public static final class Builder {
        @Nullable
        private String model;
        @Nullable
        private Double temperature;
        @Nullable
        private Integer maxTokens;
        @Nullable
        private Double topP;
        private List<String> stop = List.of();
        private Map<String, Object> extraBody = Map.of();

        public Builder model(String model) { this.model = model; return this; }
        public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
        public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
        public Builder topP(Double topP) { this.topP = topP; return this; }
        public Builder stop(List<String> stop) { this.stop = stop; return this; }
        public Builder extraBody(Map<String, Object> extraBody) { this.extraBody = extraBody; return this; }
        public Builder extraBody(String key, Object value) {
            if (this.extraBody == null || this.extraBody.isEmpty()) {
                this.extraBody = new HashMap<>();
            } else {
                this.extraBody = new HashMap<>(this.extraBody);
            }
            this.extraBody.put(key, value);
            return this;
        }

        public ModelOptions build() {
            return new ModelOptions(model, temperature, maxTokens, topP, stop, extraBody);
        }
    }
}
