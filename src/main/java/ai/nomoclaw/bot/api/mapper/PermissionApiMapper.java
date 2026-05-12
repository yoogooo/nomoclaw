package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.permission.request.UpdatePermissionRulesRequest;
import ai.nomoclaw.bot.api.dto.permission.response.PermissionRulePayload;
import ai.nomoclaw.bot.api.dto.permission.response.PermissionRulesResponse;
import ai.nomoclaw.bot.application.command.UpdatePermissionRulesCommand;
import ai.nomoclaw.bot.application.dto.PermissionRuleDto;
import ai.nomoclaw.bot.application.dto.PermissionRulesDto;

import java.util.List;

/**
 * Maps permission API payloads to application-layer contracts and back.
 */
public final class PermissionApiMapper {

    private PermissionApiMapper() {
    }

    public static UpdatePermissionRulesCommand toCommand(UpdatePermissionRulesRequest request) {
        if (request == null || request.rules() == null) {
            return new UpdatePermissionRulesCommand(List.of());
        }
        return new UpdatePermissionRulesCommand(
                request.rules().stream()
                        .filter(item -> item != null)
                        .map(PermissionApiMapper::toDto)
                        .toList()
        );
    }

    public static PermissionRulesResponse toResponse(PermissionRulesDto dto) {
        return new PermissionRulesResponse(
                dto.agentUid(),
                dto.agentName(),
                dto.sessionRules().stream().map(PermissionApiMapper::toPayload).toList(),
                dto.commandRules().stream().map(PermissionApiMapper::toPayload).toList(),
                dto.agentSettingsRules().stream().map(PermissionApiMapper::toPayload).toList(),
                dto.userSettingsRules().stream().map(PermissionApiMapper::toPayload).toList(),
                dto.hardGuardProtectedNames(),
                dto.hardGuardSystemRoots()
        );
    }

    private static PermissionRuleDto toDto(PermissionRulePayload payload) {
        return new PermissionRuleDto(
                payload.ruleId(),
                payload.effect(),
                payload.tool(),
                payload.action(),
                payload.resourceType(),
                payload.pathPattern(),
                payload.commandPattern(),
                payload.expiresAt(),
                payload.enabled()
        );
    }

    private static PermissionRulePayload toPayload(PermissionRuleDto dto) {
        return new PermissionRulePayload(
                dto.ruleId(),
                dto.effect(),
                dto.tool(),
                dto.action(),
                dto.resourceType(),
                dto.pathPattern(),
                dto.commandPattern(),
                dto.expiresAt(),
                dto.enabled()
        );
    }
}
