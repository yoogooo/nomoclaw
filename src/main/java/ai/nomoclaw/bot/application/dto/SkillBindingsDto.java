package ai.nomoclaw.bot.application.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SkillBindingsDto(
        String skillKey,
        String displayName,
        String description,
        String skillPath,
        String status,
        LocalDateTime updatedTime,
        int enabledAgentCount,
        List<SkillBindingAgentDto> agentBindings
) {
}
