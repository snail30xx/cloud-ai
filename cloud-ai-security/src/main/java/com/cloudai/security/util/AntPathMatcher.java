package com.cloudai.security.util;

import java.util.regex.Pattern;

/**
 * Ant 风格路径匹配器 — 替代 Spring AntPathMatcher。
 *
 * <p>支持通配符：
 * <ul>
 *   <li> ? — 匹配单字符（非路径分隔符）</li>
 *   <li> * — 匹配零或多个字符（非路径分隔符）</li>
 *   <li> ** — 匹配零或多个路径层级（含路径分隔符）</li>
 * </ul>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class AntPathMatcher {

    private AntPathMatcher() {}

    /**
     * 判断给定路径是否匹配 Ant 风格模式。
     *
     * @param pattern Ant 模式（如 /workspace/**）
     * @param path    实际路径
     * @return 匹配返回 true
     */
    public static boolean match(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        if (pattern.equals(path)) {
            return true;
        }
        var regex = antPatternToRegex(pattern);
        return Pattern.matches(regex, path);
    }

    /** 将 Ant 模式转换为正则表达式。 */
    private static String antPatternToRegex(String pattern) {
        var sb = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i += 2;
                    if (i < pattern.length() && pattern.charAt(i) == '/') {
                        i++;
                    }
                } else {
                    sb.append("[^/]*");
                    i++;
                }
            } else if (c == '?') {
                sb.append("[^/]");
                i++;
            } else if (c == '.') {
                sb.append("\\.");
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}