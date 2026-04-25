package ai.nomoclaw.bot.api;

import java.util.List;

public record UpdatePermissionRulesRequest(
        java.util.List<PermissionRulePayload> rules
) {
}
