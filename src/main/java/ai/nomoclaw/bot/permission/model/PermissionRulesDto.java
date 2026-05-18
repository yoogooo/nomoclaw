package ai.nomoclaw.bot.permission.model;

import java.util.List;

public record PermissionRulesDto(
        String agentUid,
        String agentName,
        List<PermissionRuleDto> sessionRules,
        List<PermissionRuleDto> commandRules,
        List<PermissionRuleDto> agentSettingsRules,
        List<PermissionRuleDto> userSettingsRules,
        List<String> hardGuardProtectedNames,
        List<String> hardGuardSystemRoots
) {
}
