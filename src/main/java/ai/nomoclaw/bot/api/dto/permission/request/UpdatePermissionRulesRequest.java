package ai.nomoclaw.bot.api.dto.permission.request;

import ai.nomoclaw.bot.api.dto.permission.response.PermissionRulePayload;

import java.util.List;

public record UpdatePermissionRulesRequest(
        List<PermissionRulePayload> rules
) {
}
