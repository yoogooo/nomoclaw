package ai.nomoclaw.bot.orchestrator;

public record UpdatePermissionRulesRequest(
        java.util.List<PermissionRulePayload> rules
) {
}
