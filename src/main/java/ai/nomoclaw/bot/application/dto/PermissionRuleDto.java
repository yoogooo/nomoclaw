package ai.nomoclaw.bot.application.dto;

public record PermissionRuleDto(
        String ruleId,
        String effect,
        String tool,
        String action,
        String resourceType,
        String pathPattern,
        String commandPattern,
        String expiresAt,
        Boolean enabled
) {
}
