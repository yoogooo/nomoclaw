package ai.nomoclaw.bot.tip.model;

import java.time.LocalDateTime;

public record AgentTipDto(
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
