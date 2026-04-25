package ai.nomoclaw.bot.application.command;

import java.util.List;

public record UpdateSkillBindingsCommand(
        boolean enabled,
        List<SkillBindingAgentCommand> agentBindings
) {
    public record SkillBindingAgentCommand(
            String agentUid,
            boolean enabled
    ) {
    }
}
