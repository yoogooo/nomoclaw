package ai.nomoclaw.bot.prompt;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptLoaderTests {

    @Test
    void buildSystemPromptShouldContainReportStructureRulesAndKeepSectionOrder() {
        PromptLoader.PromptContext context = PromptLoader.PromptContext.forAgent(
                "session-1",
                "message-1",
                "web",
                "",
                "general_assistant",
                Path.of(".").toAbsolutePath().normalize()
        );
        String builtinToolsPrompt = "- ReadFileTool";
        String mcpToolsPrompt = "- mcp_demo_tool";
        String toolkitPrompt = "# SKILL\nskill content";

        String prompt = PromptLoader.buildSystemPrompt(
                context,
                builtinToolsPrompt,
                mcpToolsPrompt,
                toolkitPrompt,
                "fallback"
        );

        assertTrue(prompt.contains("# REPORT_OUTPUT_STRUCTURE (MUST)"));
        assertTrue(prompt.contains("report/<topic>/<yyyy-mm-dd>/<hhmm>-<short-task>.md"));
        assertTrue(prompt.contains("If the user explicitly provides a custom output path, follow the user's instruction."));
        assertTrue(prompt.contains("report/ 目录禁止平铺，必须按 topic/date 分层"));

        int envIndex = prompt.indexOf("# ENVIRONMENT");
        int reportRuleIndex = prompt.indexOf("# REPORT_OUTPUT_STRUCTURE (MUST)");
        int builtinIndex = prompt.indexOf("[内置工具]");
        int mcpIndex = prompt.indexOf("[MCP 工具]");
        int toolkitIndex = prompt.indexOf("# SKILL");

        assertTrue(envIndex >= 0);
        assertTrue(reportRuleIndex > envIndex);
        assertTrue(builtinIndex > reportRuleIndex);
        assertTrue(mcpIndex > builtinIndex);
        assertTrue(toolkitIndex > mcpIndex);
    }
}

