package ai.nomoclaw.bot.model;

import tools.jackson.databind.JsonNode;

import java.nio.file.Path;

public record ToolRequest(
        String conversationUid,
        String messageUid,
        String stepUid,
        String agentUid,
        String agentName,
        Path agentWorkspacePath,
        Path tmpDirectory,
        Path reportDirectory,
        JsonNode args,
        long timeoutMs
) {
}
