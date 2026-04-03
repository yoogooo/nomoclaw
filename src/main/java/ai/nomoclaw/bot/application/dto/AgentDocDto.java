package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;

public record AgentDocDto(
        String key,
        String fileName,
        String content,
        LocalDateTime updatedTime
) {
}
