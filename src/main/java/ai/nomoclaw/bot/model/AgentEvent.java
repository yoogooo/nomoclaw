package ai.nomoclaw.bot.model;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record AgentEvent(
        String id,
        AgentEventType eventType,
        String conversationUid,
        String messageUid,
        String stepUid,
        Instant timestamp,
        JsonNode payload
) {
}
