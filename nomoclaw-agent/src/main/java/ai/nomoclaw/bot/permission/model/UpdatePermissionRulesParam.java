package ai.nomoclaw.bot.permission.model;

import ai.nomoclaw.bot.permission.model.PermissionRuleDto;

import java.util.List;

public record UpdatePermissionRulesParam(
        List<PermissionRuleDto> rules
) {
}
