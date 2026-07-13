package ai.nomoclaw.bot.model;

import tools.jackson.databind.JsonNode;

public record ToolResult(
        boolean success,
        String output,
        JsonNode artifacts,
        String errorCode,
        String errorMessage,
        JsonNode metrics
) {
    public static ToolResult success(String output, JsonNode artifacts, JsonNode metrics) {
        return new ToolResult(true, output, artifacts, null, null, metrics);
    }

    public static ToolResult failure(String errorCode, String errorMessage, JsonNode metrics) {
        return new ToolResult(false, null, null, errorCode, errorMessage, metrics);
    }
}

