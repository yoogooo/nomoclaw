package ai.nomoclaw.bot.application.command;

public record CreateSkillCommand(
        String skillKey,
        String displayName,
        String description,
        String purpose,
        boolean attachToAgent
) {
}
