package ai.nomoclaw.bot.api;

import java.time.LocalDateTime;

public record AgentTipResponse(
        String tipUid,
        String agentUid,
        String title,
        String summary,
        String sourceContent,
        String sourceConversationUid,
        String sourceMessageUid,
        LocalDateTime sourceTime,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
