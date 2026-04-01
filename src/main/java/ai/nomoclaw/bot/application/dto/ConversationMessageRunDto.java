package ai.nomoclaw.bot.application.dto;

import java.time.Instant;
import java.util.List;

public record ConversationMessageRunDto(
        String messageUid,
        String status,
        String summary,
        int completedSteps,
        int totalSteps,
        Instant updatedTime,
        List<ConversationRunStepDto> steps
) {
}
