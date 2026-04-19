package ai.nomoclaw.bot.api;

import java.util.List;

public record PermissionRulesResponse(
        String agentUid,
        String agentName,
        java.util.List<PermissionRulePayload> sessionRules,
        java.util.List<PermissionRulePayload> commandRules,
        java.util.List<PermissionRulePayload> agentSettingsRules,
        java.util.List<PermissionRulePayload> userSettingsRules,
        java.util.List<String> hardGuardProtectedNames,
        java.util.List<String> hardGuardSystemRoots
) {
}
