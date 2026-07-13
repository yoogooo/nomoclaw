package ai.nomoclaw.bot.skill.model;

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
