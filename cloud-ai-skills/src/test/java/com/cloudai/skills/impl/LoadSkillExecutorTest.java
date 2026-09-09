package com.cloudai.skills.impl;

import com.cloudai.core.model.ToolCall;
import com.cloudai.execution.model.ToolResult;
import com.cloudai.skills.spi.SkillLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoadSkillExecutor")
class LoadSkillExecutorTest {

    @Nested
    @DisplayName("Correct path")
    class Correct {

        @Test
        @DisplayName("加载已注册技能返回内容")
        void loadsExistingSkill() {
            SkillLoader loader = name -> "Skill body for " + name;
            var executor = new LoadSkillExecutor(loader);
            var toolCall = new ToolCall("call-1", "load_skill", "{\"name\":\"my-skill\"}");
            var result = executor.execute(toolCall);
            assertTrue(result.success());
            assertEquals("Skill body for my-skill", result.output());
        }
    }

    @Nested
    @DisplayName("Border / Error")
    class BorderError {

        @Test
        @DisplayName("缺少 name 参数返回失败")
        void missingName() {
            SkillLoader loader = name -> "body";
            var executor = new LoadSkillExecutor(loader);
            var toolCall = new ToolCall("call-2", "load_skill", "{}");
            var result = executor.execute(toolCall);
            assertFalse(result.success());
            assertTrue(result.error().contains("Missing"));
        }

        @Test
        @DisplayName("技能不存在返回失败")
        void skillNotFound() {
            SkillLoader loader = name -> null;
            var executor = new LoadSkillExecutor(loader);
            var toolCall = new ToolCall("call-3", "load_skill", "{\"name\":\"unknown\"}");
            var result = executor.execute(toolCall);
            assertFalse(result.success());
            assertTrue(result.error().contains("not found"));
        }

        @Test
        @DisplayName("空参数返回失败")
        void blankArguments() {
            SkillLoader loader = name -> "body";
            var executor = new LoadSkillExecutor(loader);
            var toolCall = new ToolCall("call-4", "load_skill", "");
            var result = executor.execute(toolCall);
            assertFalse(result.success());
        }
    }
}
