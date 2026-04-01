package ai.nomoclaw.bot.api;

import ai.nomoclaw.bot.application.command.CreateAgentCommand;
import ai.nomoclaw.bot.application.command.CreateAgentTipCommand;
import ai.nomoclaw.bot.application.command.CreateCronJobCommand;
import ai.nomoclaw.bot.application.command.CreateSkillCommand;
import ai.nomoclaw.bot.application.command.ImportSkillFromUrlCommand;
import ai.nomoclaw.bot.application.command.UpdateAgentBasicInfoCommand;
import ai.nomoclaw.bot.application.command.UpdateCronJobCommand;
import ai.nomoclaw.bot.application.dto.*;
import ai.nomoclaw.bot.application.dto.AgentSkillDto;
import ai.nomoclaw.bot.application.dto.AgentTipDto;
import ai.nomoclaw.bot.application.dto.BatchDeleteCronJobsDto;
import ai.nomoclaw.bot.application.dto.ChannelConfigDto;
import ai.nomoclaw.bot.application.dto.CronJobDto;
import ai.nomoclaw.bot.application.dto.CronJobExecutionResultDto;
import ai.nomoclaw.bot.application.dto.CronJobReportDto;
import ai.nomoclaw.bot.application.dto.MessageFileLinkDto;
import ai.nomoclaw.bot.application.dto.SystemConfigDto;
import ai.nomoclaw.bot.scheduler.CronSubscriptionRepository;

import java.util.List;

// TODO: Consider migrating manual mappings to MapStruct when mapper count grows,
// to reduce boilerplate and lower missing-field mapping risk.
public final class ApiDtoMapper {

    private ApiDtoMapper() {
    }

    public static List<ConversationSummaryResponse> toConversationSummaries(List<ConversationSummaryDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toConversationSummary).toList();
    }

    public static ConversationSummaryResponse toConversationSummary(ConversationSummaryDto dto) {
        return new ConversationSummaryResponse(
                dto.conversationUid(),
                dto.agentGroupUid(),
                dto.agentUid(),
                dto.title(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static List<AgentCatalogGroupResponse> toAgentCatalogGroups(List<AgentCatalogGroupDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toAgentCatalogGroup).toList();
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
                dto.agents().stream().map(ApiDtoMapper::toAgentCatalogAgent).toList()
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
                dto.sortIndex(),
                dto.capabilityTags(),
                dto.memberRole(),
                dto.responsibility(),
                dto.primary()
        );
    }

    public static List<AgentSkillResponse> toAgentSkills(List<AgentSkillDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toAgentSkill).toList();
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

    public static List<AgentToolResponse> toAgentTools(List<AgentToolDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toAgentTool).toList();
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

    public static List<AgentTipResponse> toAgentTips(List<AgentTipDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toAgentTip).toList();
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

    public static List<ConversationMessageResponse> toConversationMessages(List<ConversationMessageDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toConversationMessage).toList();
    }

    public static ConversationMessageResponse toConversationMessage(ConversationMessageDto dto) {
        return new ConversationMessageResponse(
                dto.messageUid(),
                dto.parentMessageUid(),
                dto.role(),
                dto.content(),
                dto.status(),
                dto.provider(),
                dto.modelName(),
                dto.inputTokens(),
                dto.outputTokens(),
                dto.totalTokens(),
                dto.createdTime(),
                dto.fileLinks().stream().map(ApiDtoMapper::toMessageFileLink).toList(),
                dto.attachments().stream().map(ApiDtoMapper::toConversationAttachment).toList()
        );
    }

    public static MessageFileLinkResponse toMessageFileLink(MessageFileLinkDto dto) {
        return new MessageFileLinkResponse(dto.name(), dto.path());
    }

    public static ConversationAttachmentResponse toConversationAttachment(ConversationAttachmentDto dto) {
        return new ConversationAttachmentResponse(
                dto.uploadUid(),
                dto.name(),
                dto.contentType(),
                dto.mimeGroup(),
                dto.sizeBytes(),
                dto.fileUrl(),
                dto.previewable()
        );
    }

    public static List<ConversationMessageRunResponse> toMessageRuns(List<ConversationMessageRunDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toMessageRun).toList();
    }

    public static ConversationMessageRunResponse toMessageRun(ConversationMessageRunDto dto) {
        return new ConversationMessageRunResponse(
                dto.messageUid(),
                dto.status(),
                dto.summary(),
                dto.completedSteps(),
                dto.totalSteps(),
                dto.updatedTime(),
                dto.steps().stream().map(ApiDtoMapper::toRunStep).toList()
        );
    }

    public static ConversationRunStepResponse toRunStep(ConversationRunStepDto dto) {
        return new ConversationRunStepResponse(
                dto.stepUid(),
                dto.roundIndex(),
                dto.stepIndex(),
                dto.status(),
                dto.displayTitle(),
                dto.displaySummary(),
                dto.displayDetails(),
                dto.updatedTime()
        );
    }

    public static SystemConfigResponse toSystemConfig(SystemConfigDto dto) {
        return new SystemConfigResponse(dto.nomoclawRootDir(), dto.agentsRootDir(), dto.skillsRootDir());
    }

    public static ChannelConfigResponse toChannelConfig(ChannelConfigDto dto) {
        return new ChannelConfigResponse(
                new ChannelConfigResponse.Channels(
                        new ChannelConfigResponse.Feishu(
                                dto.channels().feishu().enabled(),
                                dto.channels().feishu().requireMention(),
                                dto.channels().feishu().allowList(),
                                dto.channels().feishu().appId(),
                                dto.channels().feishu().appSecret(),
                                dto.channels().feishu().processingAckReactionEnabled(),
                                dto.channels().feishu().processingAckReactionType()
                        ),
                        new ChannelConfigResponse.DingTalk(
                                dto.channels().dingtalk().enabled(),
                                dto.channels().dingtalk().requireMention(),
                                dto.channels().dingtalk().allowList(),
                                dto.channels().dingtalk().clientId(),
                                dto.channels().dingtalk().clientSecret(),
                                dto.channels().dingtalk().robotCode()
                        )
                )
        );
    }

    public static ModelConfigResponse toModelConfig(ModelConfigDto dto) {
        return new ModelConfigResponse(
                dto.providers().stream()
                        .map(provider -> new ModelConfigResponse.Provider(
                                provider.id(),
                                provider.name(),
                                provider.protocol(),
                                provider.local(),
                                provider.requireApiKey(),
                                provider.freezeUrl(),
                                provider.baseUrl(),
                                provider.apiKey(),
                                provider.defaultModel(),
                                provider.models().stream()
                                        .map(model -> new ModelConfigResponse.Model(
                                                model.id(),
                                                model.name(),
                                                model.capabilities(),
                                                model.reasoning(),
                                                model.contextWindow(),
                                                model.maxInputTokens(),
                                                model.maxOutputTokens(),
                                                toUploadPolicy(model.uploadPolicy())
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }

    public static ModelConfigDto toModelConfig(UpdateModelConfigRequest request) {
        if (request == null || request.providers() == null) {
            return new ModelConfigDto(List.of());
        }
        return new ModelConfigDto(
                request.providers().stream()
                        .map(provider -> new ModelConfigDto.Provider(
                                provider.id(),
                                provider.name(),
                                provider.protocol(),
                                provider.local(),
                                provider.requireApiKey(),
                                provider.freezeUrl(),
                                provider.baseUrl(),
                                provider.apiKey(),
                                provider.defaultModel(),
                                provider.models() == null ? List.of() : provider.models().stream()
                                        .map(model -> new ModelConfigDto.Model(
                                                model.id(),
                                                model.name(),
                                                model.capabilities(),
                                                model.reasoning(),
                                                model.contextWindow(),
                                                model.maxInputTokens(),
                                                model.maxOutputTokens(),
                                                toUploadPolicy(model.uploadPolicy())
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }

    private static ModelConfigResponse.UploadPolicy toUploadPolicy(ModelConfigDto.UploadPolicy policy) {
        if (policy == null) {
            return null;
        }
        return new ModelConfigResponse.UploadPolicy(
                policy.enabled(),
                policy.allowedMimeGroups(),
                policy.maxFilesPerMessage(),
                policy.maxImagesPerMessage(),
                policy.singleMimeGroupOnly(),
                policy.allowMixedImageAndFile()
        );
    }

    private static ModelConfigDto.UploadPolicy toUploadPolicy(UpdateModelConfigRequest.UploadPolicy policy) {
        if (policy == null) {
            return null;
        }
        return new ModelConfigDto.UploadPolicy(
                policy.enabled(),
                policy.allowedMimeGroups(),
                policy.maxFilesPerMessage(),
                policy.maxImagesPerMessage(),
                policy.singleMimeGroupOnly(),
                policy.allowMixedImageAndFile()
        );
    }

    public static List<CronJobResponse> toCronJobs(List<CronJobDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toCronJob).toList();
    }

    public static CronJobResponse toCronJob(CronJobDto dto) {
        return new CronJobResponse(
                dto.jobUid(),
                dto.agentUid(),
                dto.agentName(),
                dto.agentDisplayName(),
                dto.agentAvatar(),
                dto.title(),
                dto.registered(),
                dto.triggerState(),
                dto.expression(),
                dto.timezone(),
                dto.endAt(),
                dto.taskContent(),
                dto.status(),
                dto.lastRunTime(),
                dto.nextRunTime(),
                dto.lastResult(),
                dto.lastReportPath(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static CronJobReportResponse toCronJobReport(CronJobReportDto dto) {
        return new CronJobReportResponse(dto.jobUid(), dto.reportPath(), dto.content(), dto.updatedTime());
    }

    public static List<CronJobExecutionResultResponse> toCronJobExecutionResults(List<CronJobExecutionResultDto> dtos) {
        return dtos.stream()
                .map(item -> new CronJobExecutionResultResponse(
                        item.executedTime(),
                        item.status(),
                        item.summary(),
                        item.reportPath(),
                        item.reportContent()
                ))
                .toList();
    }

    public static BatchDeleteCronJobsResponse toBatchDeleteResult(BatchDeleteCronJobsDto dto) {
        return new BatchDeleteCronJobsResponse(
                dto.requestedCount(),
                dto.deletedJobUids(),
                dto.failedItems().stream()
                        .map(item -> new BatchDeleteCronJobsResponse.FailedItem(item.jobUid(), item.reason()))
                        .toList()
        );
    }

    public static List<CronSubscriptionResponse> toCronSubscriptions(List<CronSubscriptionDto> dtos) {
        return dtos.stream().map(ApiDtoMapper::toCronSubscription).toList();
    }

    public static CronSubscriptionResponse toCronSubscription(CronSubscriptionDto dto) {
        return new CronSubscriptionResponse(
                dto.subscriptionUid(),
                dto.jobUid(),
                dto.channel(),
                dto.target(),
                dto.enabled(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static UpdateAgentBasicInfoCommand toCommand(UpdateAgentBasicInfoRequest request) {
        return new UpdateAgentBasicInfoCommand(
                request.displayName(),
                request.description(),
                request.avatar(),
                request.avatarColor(),
                request.modelProvider(),
                request.modelName(),
                request.modelNames()
        );
    }

    public static CreateAgentCommand toCommand(CreateAgentRequest request) {
        return new CreateAgentCommand(
                request == null ? null : request.agentName(),
                request == null ? null : request.displayName(),
                request == null ? null : request.description(),
                request == null ? null : request.avatar(),
                request == null ? null : request.avatarColor(),
                request == null ? null : request.modelProvider(),
                request == null ? null : request.modelName(),
                request == null ? null : request.modelNames()
        );
    }

    public static CreateAgentTipCommand toCommand(CreateAgentTipRequest request) {
        return new CreateAgentTipCommand(
                request == null ? null : request.title(),
                request == null ? null : request.summary(),
                request == null ? null : request.sourceContent(),
                request == null ? null : request.sourceConversationUid(),
                request == null ? null : request.sourceMessageUid(),
                request == null ? null : request.sourceTime()
        );
    }

    public static CreateCronJobCommand toCommand(CreateCronJobRequest request) {
        return new CreateCronJobCommand(
                request == null ? null : request.agentUid(),
                request == null ? null : request.title(),
                request == null ? null : request.expression(),
                request == null ? null : request.timezone(),
                request == null ? null : request.endAt(),
                request == null ? null : request.taskContent(),
                request == null ? null : request.status()
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

    public static UpdateCronJobCommand toCommand(UpdateCronJobRequest request) {
        return new UpdateCronJobCommand(
                request == null ? null : request.title(),
                request == null ? null : request.expression(),
                request == null ? null : request.timezone(),
                request == null ? null : request.endAt(),
                request == null ? null : request.taskContent(),
                request == null ? null : request.status()
        );
    }

    public static List<CronSubscriptionRepository.CronSubscriptionUpsert> toCronSubscriptions(UpdateCronSubscriptionsRequest request) {
        if (request == null || request.subscriptions() == null) {
            return List.of();
        }
        return request.subscriptions().stream()
                .filter(item -> item != null)
                .map(item -> new CronSubscriptionRepository.CronSubscriptionUpsert(
                        item.channel(),
                        item.target(),
                        item.enabled()
                ))
                .toList();
    }

    public static ChannelConfigDto toChannelConfig(UpdateChannelConfigRequest request) {
        if (request == null || request.channels() == null) {
            throw new IllegalArgumentException("channels must not be null");
        }
        if (request.channels().feishu() == null || request.channels().dingtalk() == null) {
            throw new IllegalArgumentException("channels.feishu and channels.dingtalk must not be null");
        }
        return new ChannelConfigDto(
                new ChannelConfigDto.Channels(
                        new ChannelConfigDto.Feishu(
                                request.channels().feishu().enabled(),
                                request.channels().feishu().requireMention(),
                                request.channels().feishu().allowList(),
                                request.channels().feishu().appId(),
                                request.channels().feishu().appSecret(),
                                request.channels().feishu().processingAckReactionEnabled(),
                                request.channels().feishu().processingAckReactionType()
                        ),
                        new ChannelConfigDto.DingTalk(
                                request.channels().dingtalk().enabled(),
                                request.channels().dingtalk().requireMention(),
                                request.channels().dingtalk().allowList(),
                                request.channels().dingtalk().clientId(),
                                request.channels().dingtalk().clientSecret(),
                                request.channels().dingtalk().robotCode()
                        )
                )
        );
    }
}
