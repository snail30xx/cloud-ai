package com.cloudai.skills.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FilesystemSkillRegistry")
class FilesystemSkillRegistryTest {

    @TempDir
    Path tempDir;

    private void createSkill(String dir, String name, String description, String triggers) throws IOException {
        var skillDir = tempDir.resolve(dir).resolve(name);
        Files.createDirectories(skillDir);
        var content = "---\nname: " + name + "\ndescription: " + description + "\ntriggers: " + triggers + "\n---\n# " + name + "\nSkill body content.";
        Files.writeString(skillDir.resolve("SKILL.md"), content);
    }

    @Nested
    @DisplayName("Correct path")
    class Correct {

        @Test
        @DisplayName("扫描 .agents/skills 目录")
        void scansAgentsSkills() throws IOException {
            createSkill(".agents/skills", "code-review", "Review code", "[code review, audit]");
            var registry = new FilesystemSkillRegistry(tempDir);
            assertEquals(1, registry.list().size());
            var skill = registry.find("code-review");
            assertNotNull(skill);
            assertEquals("Review code", skill.description());
            assertEquals(2, skill.triggers().size());
        }

        @Test
        @DisplayName("扫描 .claude/skills 目录")
        void scansClaudeSkills() throws IOException {
            createSkill(".claude/skills", "java-test", "Test Java", "[test, junit]");
            var registry = new FilesystemSkillRegistry(tempDir);
            assertEquals(1, registry.list().size());
        }

        @Test
        @DisplayName("同时扫描两个目录")
        void scansBothDirs() throws IOException {
            createSkill(".agents/skills", "skill-a", "Desc A", "[]");
            createSkill(".claude/skills", "skill-b", "Desc B", "[]");
            var registry = new FilesystemSkillRegistry(tempDir);
            assertEquals(2, registry.list().size());
        }

        @Test
        @DisplayName("load 返回不含 frontmatter 的 body")
        void loadReturnsBody() throws IOException {
            createSkill(".agents/skills", "my-skill", "My desc", "[]");
            var registry = new FilesystemSkillRegistry(tempDir);
            var body = registry.load("my-skill");
            assertNotNull(body);
            assertFalse(body.contains("---"));
            assertTrue(body.contains("Skill body content."));
        }
    }

    @Nested
    @DisplayName("Border / Error")
    class BorderError {

        @Test
        @DisplayName("目录不存在时返回空列表")
        void noDirsReturnsEmpty() {
            var registry = new FilesystemSkillRegistry(tempDir);
            assertTrue(registry.list().isEmpty());
        }

        @Test
        @DisplayName("load 不存在的技能返回 null")
        void loadMissingReturnsNull() throws IOException {
            var registry = new FilesystemSkillRegistry(tempDir);
            assertNull(registry.load("nonexistent"));
        }

        @Test
        @DisplayName("frontmatter 无 name 时用目录名")
        void missingNameUsesDirName() throws IOException {
            var skillDir = tempDir.resolve(".agents/skills").resolve("fallback-name");
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), "---\ndescription: No name\n---\nBody");
            var registry = new FilesystemSkillRegistry(tempDir);
            var skill = registry.find("fallback-name");
            assertNotNull(skill);
            assertEquals("fallback-name", skill.name());
        }
    }
}
