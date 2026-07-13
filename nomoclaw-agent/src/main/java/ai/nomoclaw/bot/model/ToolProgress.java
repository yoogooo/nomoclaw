package ai.nomoclaw.bot.model;

import tools.jackson.databind.JsonNode;

public record ToolProgress(
        String summary,
        String details,
        JsonNode metrics
) {
}
