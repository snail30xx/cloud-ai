package com.cloudai.core.prompt;

/**
 * 系统提示词段落 — 一个可排序、可缓存的提示词片段。
 *
 * <p>每个段落由 name、content、order、cacheable 四个维度描述。
 * 多个 PromptSection 按 {@link #order()} 升序拼接，构成最终 system prompt。</p>
 *
 * <p>order 约定（参考 Claude Code / Codex 的 static/dynamic 分界）：</p>
 * <ul>
 *   <li>{@code 10-19}：静态段落（Persona、System Rules），每次会话不变</li>
 *   <li>{@code 20-29}：静态补充段落</li>
 *   <li>{@code 30-39}：环境信息（CWD、OS、日期），会话间变化但单次会话内不变</li>
 *   <li>{@code 40-49}：项目上下文（AGENTS.md / CLAUDE.md），随项目变化</li>
 *   <li>{@code 50-59}：技能菜单（Skill 摘要），随工作目录变化</li>
 *   <li>{@code 60-69}：相关记忆，每次调用都可能变化</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public interface PromptSection {

    /** 段落名称（如 "Persona"、"Environment"），用于日志和调试。 */
    String name();

    /** 段落文本内容。 */
    String content();

    /**
     * 排序优先级，数值小的在前。
     * <p>默认返回 {@code 50}（动态段落中间值）。
     *
     * @return 排序值
     */
    default int order() {
        return 50;
    }

    /**
     * 是否可缓存（静态段落）。
     * <p>静态段落（cacheable=true）在会话内只需计算一次；
     * 动态段落（cacheable=false）每次组装时重新计算。</p>
     * <p>默认返回 false（动态）。</p>
     *
     * @return true 表示静态可缓存
     */
    default boolean cacheable() {
        return false;
    }
}
