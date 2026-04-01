package ai.nomoclaw.bot.application.command;

import java.util.List;

public record UpdateAgentBasicInfoCommand(
        String displayName,
        String description,
        String avatar,
        String avatarColor,
        String modelProvider,
        String modelName,
        List<String> modelNames
) {
}
