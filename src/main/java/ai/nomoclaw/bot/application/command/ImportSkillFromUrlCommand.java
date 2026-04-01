package ai.nomoclaw.bot.application.command;

public record ImportSkillFromUrlCommand(
        String url,
        boolean attachToAgent
) {
}
