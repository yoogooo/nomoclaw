package ai.nomoclaw.bot.policy.tool;

import tools.jackson.databind.JsonNode;

import java.nio.file.Path;

public record ToolPolicyContext(
        String toolName,
        JsonNode toolArgs,
        Path agentWorkspacePath,
        String agentUid,
        String agentName,
        String channel,
        String conversationUid,
        String messageUid,
        String stepUid
) {
}
