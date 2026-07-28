package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.system.request.UpdateChannelConfigRequest;
import ai.nomoclaw.bot.api.dto.system.request.UpdateModelConfigRequest;
import ai.nomoclaw.bot.api.dto.system.response.ChannelConfigResponse;
import ai.nomoclaw.bot.api.dto.system.response.ChannelTargetSearchResponse;
import ai.nomoclaw.bot.api.dto.system.response.ModelCatalogStatusResponse;
import ai.nomoclaw.bot.api.dto.system.response.ModelConfigResponse;
import ai.nomoclaw.bot.api.dto.system.response.SystemConfigResponse;
import ai.nomoclaw.bot.api.dto.system.response.SystemErrorLogResponse;
import ai.nomoclaw.bot.api.dto.system.response.SystemErrorLogSummaryResponse;
import ai.nomoclaw.bot.common.page.PageResult;
import ai.nomoclaw.bot.api.dto.common.response.PageResponse;
import ai.nomoclaw.bot.system.model.ChannelConfigDto;
import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;
import ai.nomoclaw.bot.system.model.SystemConfigDto;
import ai.nomoclaw.bot.system.model.SystemErrorLogDto;
import ai.nomoclaw.bot.system.model.SystemErrorLogSummaryDto;
import ai.nomoclaw.bot.modelconfig.ModelCatalogStatusDto;
import ai.nomoclaw.bot.scheduler.CronChannelTargetDirectoryService;

import java.util.List;

/**
 * Maps System domain request/response payloads.
 */
public final class SystemApiMapper {

    private SystemApiMapper() {
    }

    public static SystemConfigResponse toSystemConfig(SystemConfigDto dto) {
        return new SystemConfigResponse(dto.nomoclawRootDir(), dto.agentsRootDir(), dto.skillsRootDir());
    }

    public static List<SystemErrorLogResponse> toSystemErrorLogs(List<SystemErrorLogDto> dtos) {
        return dtos.stream().map(SystemApiMapper::toSystemErrorLog).toList();
    }

    public static PageResponse<SystemErrorLogResponse> toSystemErrorLogPage(PageResult<SystemErrorLogDto> pageResult) {
        return new PageResponse<>(
                toSystemErrorLogs(pageResult.items()),
                pageResult.total(),
                pageResult.page(),
                pageResult.pageSize(),
                pageResult.totalPages()
        );
    }

    public static SystemErrorLogResponse toSystemErrorLog(SystemErrorLogDto dto) {
        return new SystemErrorLogResponse(
                dto.logUid(),
                dto.level(),
                dto.source(),
                dto.code(),
                dto.title(),
                dto.message(),
                dto.detail(),
                dto.occurredTime()
        );
    }

    public static SystemErrorLogSummaryResponse toSystemErrorLogSummary(SystemErrorLogSummaryDto dto) {
        return new SystemErrorLogSummaryResponse(
                dto.hasErrors(),
                dto.recent24hCount(),
                dto.latestOccurredTime()
        );
    }

    public static ChannelConfigResponse toChannelConfig(ChannelConfigDto dto) {
        return new ChannelConfigResponse(
                new ChannelConfigResponse.Channels(
                        new ChannelConfigResponse.Feishu(
                                dto.channels().feishu().enabled(),
                                dto.channels().feishu().bots().stream()
                                        .map(bot -> new ChannelConfigResponse.FeishuBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.appId(),
                                                bot.appSecret(),
                                                bot.processingAckReactionEnabled(),
                                                bot.processingAckReactionType(),
                                                bot.defaultTarget(),
                                                bot.defaultTargetDisplayName(),
                                                bot.targetResolvedAt(),
                                                bot.markdownEnabled()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigResponse.DingTalk(
                                dto.channels().dingtalk().enabled(),
                                dto.channels().dingtalk().bots().stream()
                                        .map(bot -> new ChannelConfigResponse.DingTalkBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.clientId(),
                                                bot.clientSecret(),
                                                bot.robotCode()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigResponse.Discord(
                                dto.channels().discord().enabled(),
                                dto.channels().discord().bots().stream()
                                        .map(bot -> new ChannelConfigResponse.DiscordBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.token(),
                                                bot.botUserId(),
                                                bot.acceptBotMessages()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigResponse.Telegram(
                                dto.channels().telegram().enabled(),
                                dto.channels().telegram().bots().stream()
                                        .map(bot -> new ChannelConfigResponse.TelegramBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.token(),
                                                bot.botUsername()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigResponse.Qq(
                                dto.channels().qq().enabled(),
                                dto.channels().qq().bots().stream()
                                        .map(bot -> new ChannelConfigResponse.QqBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.appId(),
                                                bot.clientSecret(),
                                                bot.botUserId(),
                                                bot.sandbox(),
                                                bot.markdownEnabled()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigResponse.WeCom(
                                dto.channels().wecom().enabled(),
                                dto.channels().wecom().bots().stream()
                                        .map(bot -> new ChannelConfigResponse.WeComBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.wecomBotId(),
                                                bot.secret()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigResponse.Weixin(
                                dto.channels().weixin().enabled(),
                                dto.channels().weixin().bots().stream()
                                        .map(bot -> new ChannelConfigResponse.WeixinBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.botToken(),
                                                bot.botTokenFile(),
                                                bot.baseUrl()
                                        ))
                                        .toList()
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
                                provider.configured(),
                                provider.authStatus(),
                                provider.authMessage(),
                                provider.defaultModel(),
                                provider.models().stream()
                                        .map(model -> new ModelConfigResponse.Model(
                                                model.id(),
                                                model.name(),
                                                model.modelType(),
                                                model.capabilities(),
                                                model.reasoning(),
                                                model.contextWindow(),
                                                model.maxInputTokens(),
                                                model.maxOutputTokens(),
                                                toUploadPolicy(model.uploadPolicy()),
                                                model.catalogMatched(),
                                                model.catalogSource()
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
                                false,
                                "missing",
                                "",
                                provider.defaultModel(),
                                provider.models() == null ? List.of() : provider.models().stream()
                                        .map(model -> new ModelConfigDto.Model(
                                                model.id(),
                                                model.name(),
                                                model.modelType(),
                                                model.capabilities(),
                                                model.reasoning(),
                                                model.contextWindow(),
                                                model.maxInputTokens(),
                                                model.maxOutputTokens(),
                                                toUploadPolicy(model.uploadPolicy()),
                                                false,
                                                "request"
                                        ))
                                        .toList()
                        ))
                        .toList()
        );
    }

    public static ModelCatalogStatusResponse toModelCatalogStatus(ModelCatalogStatusDto dto) {
        return new ModelCatalogStatusResponse(
                dto.catalogVersion(),
                dto.generatedAt(),
                dto.source(),
                dto.stale(),
                dto.message()
        );
    }

    public static ChannelTargetSearchResponse toChannelTargetSearch(CronChannelTargetDirectoryService.SearchResult result) {
        return new ChannelTargetSearchResponse(
                result.items().stream()
                        .map(item -> new ChannelTargetSearchResponse.Item(
                                item.label(),
                                item.target(),
                                item.kind(),
                                item.source()
                        ))
                        .toList(),
                result.error()
        );
    }

    public static ChannelConfigDto toChannelConfig(UpdateChannelConfigRequest request) {
        if (request == null || request.channels() == null) {
            throw new IllegalArgumentException("channels must not be null");
        }
        if (request.channels().feishu() == null || request.channels().dingtalk() == null
                || request.channels().discord() == null || request.channels().telegram() == null
                || request.channels().qq() == null || request.channels().wecom() == null || request.channels().weixin() == null) {
            throw new IllegalArgumentException("channels.feishu, channels.dingtalk, channels.discord, channels.telegram, channels.qq, channels.wecom and channels.weixin must not be null");
        }
        return new ChannelConfigDto(
                new ChannelConfigDto.Channels(
                        new ChannelConfigDto.Feishu(
                                request.channels().feishu().enabled(),
                                request.channels().feishu().bots() == null
                                        ? List.of()
                                        : request.channels().feishu().bots().stream()
                                        .map(bot -> new ChannelConfigDto.FeishuBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.appId(),
                                                bot.appSecret(),
                                                bot.processingAckReactionEnabled(),
                                                bot.processingAckReactionType(),
                                                bot.defaultTarget(),
                                                bot.defaultTargetDisplayName(),
                                                bot.targetResolvedAt(),
                                                bot.markdownEnabled()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigDto.DingTalk(
                                request.channels().dingtalk().enabled(),
                                request.channels().dingtalk().bots() == null
                                        ? List.of()
                                        : request.channels().dingtalk().bots().stream()
                                        .map(bot -> new ChannelConfigDto.DingTalkBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.clientId(),
                                                bot.clientSecret(),
                                                bot.robotCode()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigDto.Discord(
                                request.channels().discord().enabled(),
                                request.channels().discord().bots() == null
                                        ? List.of()
                                        : request.channels().discord().bots().stream()
                                        .map(bot -> new ChannelConfigDto.DiscordBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.token(),
                                                bot.botUserId(),
                                                bot.acceptBotMessages()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigDto.Telegram(
                                request.channels().telegram().enabled(),
                                request.channels().telegram().bots() == null
                                        ? List.of()
                                        : request.channels().telegram().bots().stream()
                                        .map(bot -> new ChannelConfigDto.TelegramBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.token(),
                                                bot.botUsername()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigDto.Qq(
                                request.channels().qq().enabled(),
                                request.channels().qq().bots() == null
                                        ? List.of()
                                        : request.channels().qq().bots().stream()
                                        .map(bot -> new ChannelConfigDto.QqBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.appId(),
                                                bot.clientSecret(),
                                                bot.botUserId(),
                                                bot.sandbox(),
                                                bot.markdownEnabled()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigDto.WeCom(
                                request.channels().wecom().enabled(),
                                request.channels().wecom().bots() == null
                                        ? List.of()
                                        : request.channels().wecom().bots().stream()
                                        .map(bot -> new ChannelConfigDto.WeComBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.wecomBotId(),
                                                bot.secret()
                                        ))
                                        .toList()
                        ),
                        new ChannelConfigDto.Weixin(
                                request.channels().weixin().enabled(),
                                request.channels().weixin().bots() == null
                                        ? List.of()
                                        : request.channels().weixin().bots().stream()
                                        .map(bot -> new ChannelConfigDto.WeixinBot(
                                                bot.botId(),
                                                bot.displayName(),
                                                bot.enabled(),
                                                bot.isDefault(),
                                                bot.requireMention(),
                                                bot.allowList(),
                                                bot.agentUid(),
                                                bot.defaultModelProvider(),
                                                bot.defaultModelName(),
                                                bot.botToken(),
                                                bot.botTokenFile(),
                                                bot.baseUrl()
                                        ))
                                        .toList()
                        )
                )
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
                policy.maxFileBytes(),
                policy.maxTotalBytes(),
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
                policy.maxFileBytes(),
                policy.maxTotalBytes(),
                policy.singleMimeGroupOnly(),
                policy.allowMixedImageAndFile()
        );
    }
}
