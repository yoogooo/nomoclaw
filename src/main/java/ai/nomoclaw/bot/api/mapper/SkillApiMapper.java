package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.skill.request.CreateSkillRequest;
import ai.nomoclaw.bot.api.dto.skill.request.ImportSkillFromUrlRequest;
import ai.nomoclaw.bot.api.dto.skill.request.UpdateSkillBindingsRequest;
import ai.nomoclaw.bot.api.dto.skill.response.AgentSkillResponse;
import ai.nomoclaw.bot.api.dto.skill.response.GlobalSkillLinkedAgentResponse;
import ai.nomoclaw.bot.api.dto.skill.response.GlobalSkillResponse;
import ai.nomoclaw.bot.api.dto.skill.response.SkillBindingAgentResponse;
import ai.nomoclaw.bot.api.dto.skill.response.SkillBindingsResponse;
import ai.nomoclaw.bot.skill.model.CreateSkillParam;
import ai.nomoclaw.bot.skill.model.ImportSkillFromUrlParam;
import ai.nomoclaw.bot.skill.model.UpdateSkillBindingsParam;
import ai.nomoclaw.bot.skill.model.AgentSkillDto;
import ai.nomoclaw.bot.skill.model.GlobalSkillDto;
import ai.nomoclaw.bot.skill.model.SkillBindingAgentDto;
import ai.nomoclaw.bot.skill.model.SkillBindingsDto;
import ai.nomoclaw.bot.skill.model.SkillLinkedAgentDto;

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

    public static ImportSkillFromUrlParam toParam(ImportSkillFromUrlRequest request) {
        return new ImportSkillFromUrlParam(
                request == null ? null : request.url(),
                request != null && Boolean.TRUE.equals(request.attachToAgent())
        );
    }

    public static CreateSkillParam toParam(CreateSkillRequest request) {
        return new CreateSkillParam(
                request == null ? null : request.skillKey(),
                request == null ? null : request.displayName(),
                request == null ? null : request.description(),
                request == null ? null : request.purpose(),
                request != null && Boolean.TRUE.equals(request.attachToAgent())
        );
    }

    public static UpdateSkillBindingsParam toParam(UpdateSkillBindingsRequest request) {
        return new UpdateSkillBindingsParam(
                request != null && Boolean.TRUE.equals(request.enabled()),
                request == null || request.agentBindings() == null
                        ? List.of()
                        : request.agentBindings().stream()
                        .filter(Objects::nonNull)
                        .map(item -> new UpdateSkillBindingsParam.SkillBindingAgentParam(
                                item.agentUid(),
                                Boolean.TRUE.equals(item.enabled())
                        ))
                        .toList()
        );
    }
}
