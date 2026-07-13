package ai.nomoclaw.bot.agentprofile.model;

import java.util.List;

public record CreateAgentParam(
        String agentName,
        String displayName,
        String agentType,
        String description,
        String avatar,
        String avatarColor,
        String modelProvider,
        String modelName,
        List<String> modelNames,
        String workspace,
        String codexWorkdir
) {
}
