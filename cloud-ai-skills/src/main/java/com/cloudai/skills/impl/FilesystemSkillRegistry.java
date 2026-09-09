package com.cloudai.skills.impl;

import com.cloudai.skills.model.Skill;
import com.cloudai.skills.spi.SkillRegistry;
import com.cloudai.skills.spi.SkillLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件系统技能注册表 — 扫描工作目录下的技能目录，解析 SKILL.md frontmatter。
 *
 * <p>扫描目录（按优先级）：</p>
 * <ol>
 *   <li>{@code .agents/skills/}</li>
 *   <li>{@code .claude/skills/}</li>
 * </ol>
 *
 * <p>每个技能子目录需包含 {@code SKILL.md} 文件，frontmatter 格式：</p>
 * <pre>{@code
 * ---
 * name: code-review
 * description: Review Java code for security and style
 * triggers: [code review, audit]
 * ---
 * # Skill body...
 * }</pre>
 *
 * @author cloud-ai
 * @since 1.0
 */
public class FilesystemSkillRegistry implements SkillRegistry, SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(FilesystemSkillRegistry.class);
    private static final List<String> SKILL_DIRS = List.of(".agents/skills", ".claude/skills");
    private static final String SKILL_FILE = "SKILL.md";

    private final Path workDir;
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    public FilesystemSkillRegistry(Path workDir) {
        this.workDir = workDir != null ? workDir : Path.of(".");
        scan();
    }

    private void scan() {
        for (var dir : SKILL_DIRS) {
            var skillsDir = workDir.resolve(dir);
            if (!Files.isDirectory(skillsDir)) {
                continue;
            }
            try (var stream = Files.list(skillsDir)) {
                stream.filter(Files::isDirectory)
                      .forEach(this::scanSkillDir);
            } catch (IOException e) {
                log.warn("Failed to scan skills directory {}: {}", skillsDir, e.getMessage());
            }
        }
        log.info("Skills discovered: {} (from {})", skills.size(), workDir);
    }

    private void scanSkillDir(Path skillDir) {
        var skillFile = skillDir.resolve(SKILL_FILE);
        if (!Files.isRegularFile(skillFile)) {
            return;
        }
        try {
            var content = Files.readString(skillFile);
            var frontmatter = parseFrontmatter(content);
            var name = frontmatter.get("name");
            if (name == null || name.isBlank()) {
                name = skillDir.getFileName().toString();
            }
            var description = frontmatter.getOrDefault("description", "");
            var triggers = parseTriggers(frontmatter.get("triggers"));
            var skill = new Skill(name, description, triggers, skillFile);
            skills.putIfAbsent(name, skill);
            log.debug("Skill discovered: {}", name);
        } catch (IOException e) {
            log.warn("Failed to read skill file {}: {}", skillFile, e.getMessage());
        }
    }

    static Map<String, String> parseFrontmatter(String content) {
        var map = new java.util.LinkedHashMap<String, String>();
        var lines = content.split("\n", -1);
        if (lines.length == 0 || !lines[0].trim().equals("---")) {
            return map;
        }
        for (int i = 1; i < lines.length; i++) {
            var line = lines[i];
            if (line.trim().equals("---")) {
                break;
            }
            var idx = line.indexOf(':');
            if (idx > 0) {
                var key = line.substring(0, idx).trim();
                var value = line.substring(idx + 1).trim();
                map.put(key, value);
            }
        }
        return map;
    }

    static List<String> parseTriggers(@Nullable String triggers) {
        if (triggers == null || triggers.isBlank()) {
            return List.of();
        }
        var trimmed = triggers.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        var parts = trimmed.split(",");
        var result = new ArrayList<String>();
        for (var p : parts) {
            var t = p.trim();
            if (!t.isEmpty()) {
                result.add(t);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<Skill> list() {
        return List.copyOf(skills.values());
    }

    @Override
    public @Nullable Skill find(String name) {
        return skills.get(name);
    }

    @Override
    public void register(Skill skill) {
        skills.put(skill.name(), skill);
    }

    @Override
    public @Nullable String load(String skillName) {
        var skill = skills.get(skillName);
        if (skill == null) {
            return null;
        }
        try {
            var content = Files.readString(skill.skillFile());
            return stripFrontmatter(content);
        } catch (IOException e) {
            log.warn("Failed to load skill content {}: {}", skillName, e.getMessage());
            return null;
        }
    }

    static String stripFrontmatter(String content) {
        var lines = content.split("\n", -1);
        if (lines.length == 0 || !lines[0].trim().equals("---")) {
            return content;
        }
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals("---")) {
                var sb = new StringBuilder();
                for (int j = i + 1; j < lines.length; j++) {
                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append(lines[j]);
                }
                return sb.toString().strip();
            }
        }
        return content;
    }
}
