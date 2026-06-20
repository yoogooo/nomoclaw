package ai.nomoclaw.bot.api.dto.system.response;

import java.util.List;

public record ChannelConfigResponse(
        Channels channels
) {

    public record Channels(
            Feishu feishu,
            DingTalk dingtalk,
            Discord discord,
            Telegram telegram,
            Qq qq,
            WeCom wecom,
            Weixin weixin
    ) {
    }

    public record Feishu(
            boolean enabled,
            List<FeishuBot> bots
    ) {
    }

    public record DingTalk(
            boolean enabled,
            List<DingTalkBot> bots
    ) {
    }

    public record Discord(
            boolean enabled,
            List<DiscordBot> bots
    ) {
    }

    public record Telegram(
            boolean enabled,
            List<TelegramBot> bots
    ) {
    }

    public record Qq(
            boolean enabled,
            List<QqBot> bots
    ) {
    }

    public record WeCom(
            boolean enabled,
            List<WeComBot> bots
    ) {
    }

    public record Weixin(
            boolean enabled,
            List<WeixinBot> bots
    ) {
    }

    public record FeishuBot(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            List<String> allowList,
            String agentUid,
            String defaultModelProvider,
            String defaultModelName,
            String appId,
            String appSecret,
            boolean processingAckReactionEnabled,
            String processingAckReactionType,
            String defaultTarget,
            String defaultTargetDisplayName,
            String targetResolvedAt
    ) {
    }

    public record DingTalkBot(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            List<String> allowList,
            String agentUid,
            String defaultModelProvider,
            String defaultModelName,
            String clientId,
            String clientSecret,
            String robotCode
    ) {
    }

    public record DiscordBot(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            List<String> allowList,
            String agentUid,
            String defaultModelProvider,
            String defaultModelName,
            String token,
            String botUserId,
            boolean acceptBotMessages
    ) {
    }

    public record TelegramBot(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            List<String> allowList,
            String agentUid,
            String defaultModelProvider,
            String defaultModelName,
            String token,
            String botUsername
    ) {
    }

    public record QqBot(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            List<String> allowList,
            String agentUid,
            String defaultModelProvider,
            String defaultModelName,
            String appId,
            String clientSecret,
            String botUserId,
            boolean sandbox,
            boolean markdownEnabled
    ) {
    }

    public record WeComBot(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            List<String> allowList,
            String agentUid,
            String defaultModelProvider,
            String defaultModelName,
            String wecomBotId,
            String secret
    ) {
    }

    public record WeixinBot(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            List<String> allowList,
            String agentUid,
            String defaultModelProvider,
            String defaultModelName,
            String botToken,
            String botTokenFile,
            String baseUrl
    ) {
    }
}
