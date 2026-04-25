package ai.nomoclaw.bot.model;

import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.function.Consumer;

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
        long timeoutMs,
        Consumer<ToolProgress> progressReporter
) {
    public void reportProgress(String summary, String details, JsonNode metrics) {
        if (progressReporter == null) {
            return;
        }
        progressReporter.accept(new ToolProgress(summary, details, metrics));
    }
}
