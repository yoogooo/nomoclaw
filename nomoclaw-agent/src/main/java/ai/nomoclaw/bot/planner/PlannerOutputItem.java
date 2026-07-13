package ai.nomoclaw.bot.planner;

import tools.jackson.databind.JsonNode;

public record PlannerOutputItem(
        String type,
        String text,
        String title,
        String toolName,
        JsonNode toolArgs,
        String riskLevel
) {
}
