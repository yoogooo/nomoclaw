package ai.nomoclaw.bot.skill.model;

import java.util.List;

public record UpdateSkillBindingsParam(
        boolean enabled,
        List<SkillBindingAgentParam> agentBindings
) {
    public record SkillBindingAgentParam(
            String agentUid,
            boolean enabled
    ) {
    }
}
