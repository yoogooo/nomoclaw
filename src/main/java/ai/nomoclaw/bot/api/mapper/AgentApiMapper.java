package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.agent.request.CreateAgentRequest;
import ai.nomoclaw.bot.api.dto.agent.request.CreateAgentTipRequest;
import ai.nomoclaw.bot.api.dto.agent.request.UpdateAgentBasicInfoRequest;
import ai.nomoclaw.bot.api.dto.agent.request.UpdateAgentTipRequest;
import ai.nomoclaw.bot.api.dto.agent.response.AgentCatalogAgentResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentCatalogGroupResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentDocResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentMcpToolResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentTipResponse;
import ai.nomoclaw.bot.api.dto.agent.response.AgentToolResponse;
import ai.nomoclaw.bot.agentprofile.model.CreateAgentParam;
import ai.nomoclaw.bot.tip.model.CreateAgentTipParam;
import ai.nomoclaw.bot.agentprofile.model.UpdateAgentBasicInfoParam;
import ai.nomoclaw.bot.tip.model.UpdateAgentTipParam;
import ai.nomoclaw.bot.agentprofile.model.AgentCatalogAgentDto;
import ai.nomoclaw.bot.agentprofile.model.AgentCatalogGroupDto;
import ai.nomoclaw.bot.agentprofile.model.AgentDocDto;
import ai.nomoclaw.bot.tip.model.AgentTipDto;
import ai.nomoclaw.bot.tool.model.AgentToolDto;
import ai.nomoclaw.bot.mcp.AgentMcpToolDto;

import java.util.List;

/**
 * Maps Agent domain request/response payloads.
 */
public final class AgentApiMapper {

    private AgentApiMapper() {
    }

    public static List<AgentCatalogGroupResponse> toAgentCatalogGroups(List<AgentCatalogGroupDto> dtos) {
        return dtos.stream().map(AgentApiMapper::toAgentCatalogGroup).toList();
    }

    public static AgentCatalogGroupResponse toAgentCatalogGroup(AgentCatalogGroupDto dto) {
        return new AgentCatalogGroupResponse(
                dto.agentGroupUid(),
                dto.groupName(),
                dto.displayName(),
                dto.avatar(),
                dto.description(),
                dto.sceneTags(),
                dto.collaborationMode(),
                dto.agents().stream().map(AgentApiMapper::toAgentCatalogAgent).toList()
        );
    }

    public static AgentCatalogAgentResponse toAgentCatalogAgent(AgentCatalogAgentDto dto) {
        return new AgentCatalogAgentResponse(
                dto.agentUid(),
                dto.agentName(),
                dto.displayName(),
                dto.avatar(),
                dto.avatarColor(),
                dto.description(),
                dto.modelProvider(),
                dto.modelName(),
                dto.modelNames(),
                dto.workspace(),
                dto.reportDir(),
                dto.tmpDir(),
                dto.sortIndex(),
                dto.capabilityTags(),
                dto.memberRole(),
                dto.responsibility(),
                dto.primary()
        );
    }

    public static List<AgentToolResponse> toAgentTools(List<AgentToolDto> dtos) {
        return dtos.stream().map(AgentApiMapper::toAgentTool).toList();
    }

    public static AgentToolResponse toAgentTool(AgentToolDto dto) {
        return new AgentToolResponse(
                dto.toolKey(),
                dto.displayName(),
                dto.description(),
                dto.enabled(),
                dto.updatedTime()
        );
    }

    public static List<AgentMcpToolResponse> toAgentMcpTools(List<AgentMcpToolDto> dtos) {
        return dtos.stream().map(AgentApiMapper::toAgentMcpTool).toList();
    }

    public static AgentMcpToolResponse toAgentMcpTool(AgentMcpToolDto dto) {
        return new AgentMcpToolResponse(
                dto.toolKey(),
                dto.serverUid(),
                dto.serverName(),
                dto.serverDisplayName(),
                dto.originalToolName(),
                dto.displayName(),
                dto.description(),
                dto.enabled(),
                dto.updatedTime()
        );
    }

    public static List<AgentTipResponse> toAgentTips(List<AgentTipDto> dtos) {
        return dtos.stream().map(AgentApiMapper::toAgentTip).toList();
    }

    public static AgentTipResponse toAgentTip(AgentTipDto dto) {
        return new AgentTipResponse(
                dto.tipUid(),
                dto.agentUid(),
                dto.title(),
                dto.summary(),
                dto.sourceContent(),
                dto.sourceConversationUid(),
                dto.sourceMessageUid(),
                dto.sourceTime(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static List<AgentDocResponse> toAgentDocs(List<AgentDocDto> dtos) {
        return dtos.stream().map(AgentApiMapper::toAgentDoc).toList();
    }

    public static AgentDocResponse toAgentDoc(AgentDocDto dto) {
        return new AgentDocResponse(
                dto.key(),
                dto.fileName(),
                dto.content(),
                dto.updatedTime()
        );
    }

    public static UpdateAgentBasicInfoParam toParam(UpdateAgentBasicInfoRequest request) {
        return new UpdateAgentBasicInfoParam(
                request.displayName(),
                request.description(),
                request.avatar(),
                request.avatarColor(),
                request.modelProvider(),
                request.modelName(),
                request.modelNames(),
                request.workspace()
        );
    }

    public static CreateAgentParam toParam(CreateAgentRequest request) {
        return new CreateAgentParam(
                request == null ? null : request.agentName(),
                request == null ? null : request.displayName(),
                request == null ? null : request.description(),
                request == null ? null : request.avatar(),
                request == null ? null : request.avatarColor(),
                request == null ? null : request.modelProvider(),
                request == null ? null : request.modelName(),
                request == null ? null : request.modelNames(),
                request == null ? null : request.workspace()
        );
    }

    public static CreateAgentTipParam toParam(CreateAgentTipRequest request) {
        return new CreateAgentTipParam(
                request == null ? null : request.title(),
                request == null ? null : request.summary(),
                request == null ? null : request.sourceContent(),
                request == null ? null : request.sourceConversationUid(),
                request == null ? null : request.sourceMessageUid(),
                request == null ? null : request.sourceTime(),
                request == null ? null : request.generateBestPractice()
        );
    }

    public static UpdateAgentTipParam toParam(UpdateAgentTipRequest request) {
        return new UpdateAgentTipParam(
                request == null ? null : request.title(),
                request == null ? null : request.summary(),
                request == null ? null : request.sourceContent()
        );
    }
}
