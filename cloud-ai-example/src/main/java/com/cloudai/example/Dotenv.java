package com.cloudai.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * .env 文件加载器 — 从项目根目录读取 .env 文件并解析为键值对。
 *
 * <p>Java 不像 Node.js 的 dotenv 自动加载 .env 文件，需要手动读取。
 * 加载后通过 {@link System#setProperty(String, String)} 设置为系统属性，
 * 代码用 {@link System#getProperty(String, String)} 读取。</p>
 *
 * @author cloud-ai
 * @since 1.0
 */
public final class Dotenv {

    private Dotenv() {
    }

    /**
     * 从项目根目录加载 .env 文件，设置为系统属性。
     *
     * @return 加载的键值对（空文件或不存在时返回空 Map）
     */
    public static Map<String, String> load() {
        return load(findEnvFile());
    }

    /**
     * 从指定路径加载 .env 文件。
     */
    public static Map<String, String> load(Path envFile) {
        var result = new LinkedHashMap<String, String>();
        if (envFile == null || !Files.exists(envFile)) {
            return result;
        }

        try {
            var lines = Files.readAllLines(envFile);
            for (var line : lines) {
                line = line.trim();
                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // 解析 KEY=VALUE
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                var key = line.substring(0, eq).trim();
                var value = line.substring(eq + 1).trim();
                // 去除引号
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    result.put(key, value);
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read .env: " + e.getMessage());
        }

        return result;
    }

    /**
     * 按优先级获取值：系统属性 > 环境变量。
     */
    public static String get(String key) {
        var prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        var env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return null;
    }

    /**
     * 按优先级获取值：系统属性 > 环境变量 > 默认值。
     */
    public static String get(String key, String defaultValue) {
        var val = get(key);
        return val != null ? val : defaultValue;
    }

    /**
     * 向上查找 .env 文件（当前目录 → 父目录 → 最多 5 层）。
     */
    private static Path findEnvFile() {
        var dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            var env = dir.resolve(".env");
            if (Files.exists(env)) {
                return env;
            }
            var parent = dir.getParent();
            if (parent == null || parent.equals(dir)) {
                break;
            }
            dir = parent;
        }
        return Paths.get(".env");
    }
}
