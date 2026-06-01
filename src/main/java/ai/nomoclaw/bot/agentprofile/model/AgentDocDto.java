package ai.nomoclaw.bot.agentprofile.model;

import java.time.LocalDateTime;

public record AgentDocDto(
        String key,
        String fileName,
        String content,
        LocalDateTime updatedTime
) {
}
