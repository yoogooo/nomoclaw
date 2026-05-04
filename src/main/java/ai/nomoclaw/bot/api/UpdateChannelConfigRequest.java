package ai.nomoclaw.bot.api;

import java.util.List;

public record UpdateChannelConfigRequest(
        Channels channels
) {

    public record Channels(
            Feishu feishu,
            DingTalk dingtalk,
            Discord discord,
            Telegram telegram
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

    public record FeishuBot(
            String botId,
            String displayName,
            boolean enabled,
            boolean isDefault,
            boolean requireMention,
            List<String> allowList,
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
            String token,
            String botUsername
    ) {
    }
}
