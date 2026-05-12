package ai.nomoclaw.bot.application.command;

import ai.nomoclaw.bot.application.dto.PermissionRuleDto;

import java.util.List;

public record UpdatePermissionRulesCommand(
        List<PermissionRuleDto> rules
) {
}
