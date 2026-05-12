package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.skill.request.CreateSkillRequest;
import ai.nomoclaw.bot.api.dto.skill.request.ImportSkillFromUrlRequest;
import ai.nomoclaw.bot.api.dto.skill.request.UpdateSkillBindingsRequest;
import ai.nomoclaw.bot.api.dto.skill.response.AgentSkillResponse;
import ai.nomoclaw.bot.api.dto.skill.response.GlobalSkillLinkedAgentResponse;
import ai.nomoclaw.bot.api.dto.skill.response.GlobalSkillResponse;
import ai.nomoclaw.bot.api.dto.skill.response.SkillBindingAgentResponse;
import ai.nomoclaw.bot.api.dto.skill.response.SkillBindingsResponse;
import ai.nomoclaw.bot.application.command.CreateSkillCommand;
import ai.nomoclaw.bot.application.command.ImportSkillFromUrlCommand;
import ai.nomoclaw.bot.application.command.UpdateSkillBindingsCommand;
import ai.nomoclaw.bot.application.dto.AgentSkillDto;
import ai.nomoclaw.bot.application.dto.GlobalSkillDto;
import ai.nomoclaw.bot.application.dto.SkillBindingAgentDto;
import ai.nomoclaw.bot.application.dto.SkillBindingsDto;
import ai.nomoclaw.bot.application.dto.SkillLinkedAgentDto;

import java.util.List;
import java.util.Objects;

/**
 * Maps Skill domain request/response payloads.
 */
public final class SkillApiMapper {

    private SkillApiMapper() {
    }

    public static List<AgentSkillResponse> toAgentSkills(List<AgentSkillDto> dtos) {
        return dtos.stream().map(SkillApiMapper::toAgentSkill).toList();
    }

    public static AgentSkillResponse toAgentSkill(AgentSkillDto dto) {
        return new AgentSkillResponse(
                dto.skillKey(),
                dto.displayName(),
                dto.description(),
                dto.skillPath(),
                dto.enabled(),
                dto.updatedTime()
        );
    }

    public static List<GlobalSkillResponse> toGlobalSkills(List<GlobalSkillDto> dtos) {
        return dtos.stream().map(SkillApiMapper::toGlobalSkill).toList();
    }

    public static GlobalSkillResponse toGlobalSkill(GlobalSkillDto dto) {
        return new GlobalSkillResponse(
                dto.skillKey(),
                dto.displayName(),
                dto.description(),
                dto.skillPath(),
                dto.status(),
                dto.updatedTime(),
                dto.linkedAgents().stream().map(SkillApiMapper::toGlobalSkillLinkedAgent).toList()
        );
    }

    public static GlobalSkillLinkedAgentResponse toGlobalSkillLinkedAgent(SkillLinkedAgentDto dto) {
        return new GlobalSkillLinkedAgentResponse(
                dto.agentUid(),
                dto.agentName(),
                dto.displayName()
        );
    }

    public static SkillBindingsResponse toSkillBindings(SkillBindingsDto dto) {
        return new SkillBindingsResponse(
                dto.skillKey(),
                dto.displayName(),
                dto.description(),
                dto.skillPath(),
                dto.status(),
                dto.updatedTime(),
                dto.enabledAgentCount(),
                dto.agentBindings().stream().map(SkillApiMapper::toSkillBindingAgent).toList()
        );
    }

    public static SkillBindingAgentResponse toSkillBindingAgent(SkillBindingAgentDto dto) {
        return new SkillBindingAgentResponse(
                dto.agentUid(),
                dto.agentName(),
                dto.displayName(),
                dto.enabled()
        );
    }

    public static ImportSkillFromUrlCommand toCommand(ImportSkillFromUrlRequest request) {
        return new ImportSkillFromUrlCommand(
                request == null ? null : request.url(),
                request != null && Boolean.TRUE.equals(request.attachToAgent())
        );
    }

    public static CreateSkillCommand toCommand(CreateSkillRequest request) {
        return new CreateSkillCommand(
                request == null ? null : request.skillKey(),
                request == null ? null : request.displayName(),
                request == null ? null : request.description(),
                request == null ? null : request.purpose(),
                request != null && Boolean.TRUE.equals(request.attachToAgent())
        );
    }

    public static UpdateSkillBindingsCommand toCommand(UpdateSkillBindingsRequest request) {
        return new UpdateSkillBindingsCommand(
                request != null && Boolean.TRUE.equals(request.enabled()),
                request == null || request.agentBindings() == null
                        ? List.of()
                        : request.agentBindings().stream()
                        .filter(Objects::nonNull)
                        .map(item -> new UpdateSkillBindingsCommand.SkillBindingAgentCommand(
                                item.agentUid(),
                                Boolean.TRUE.equals(item.enabled())
                        ))
                        .toList()
        );
    }
}
