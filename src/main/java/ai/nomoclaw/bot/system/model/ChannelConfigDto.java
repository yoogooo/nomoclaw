package ai.nomoclaw.bot.system.model;

import java.util.List;

public record ChannelConfigDto(
        Channels channels
) {

    public static final String DEFAULT_AGENT_UID = "agent_general_assistant";

    public static ChannelConfigDto defaults() {
        return new ChannelConfigDto(
                new Channels(
                        new Feishu(
                                false,
                                List.of(defaultFeishuBot())
                        ),
                        new DingTalk(
                                false,
                                List.of(defaultDingTalkBot())
                        ),
                        new Discord(
                                false,
                                List.of(defaultDiscordBot())
                        ),
                        new Telegram(
                                false,
                                List.of(defaultTelegramBot())
                        ),
                        new Qq(
                                false,
                                List.of(defaultQqBot())
                        ),
                        new WeCom(
                                false,
                                List.of(defaultWeComBot())
                        ),
                        new Weixin(
                                false,
                                List.of(defaultWeixinBot())
                        )
                )
        );
    }

    public static FeishuBot defaultFeishuBot() {
        return new FeishuBot(
                "default",
                "Feishu Default",
                false,
                true,
                true,
                List.of(),
                DEFAULT_AGENT_UID,
                "",
                "",
                "",
                "",
                true,
                "OK",
                "",
                "",
                ""
        );
    }

    public static DingTalkBot defaultDingTalkBot() {
        return new DingTalkBot(
                "default",
                "DingTalk Default",
                false,
                true,
                true,
                List.of(),
                DEFAULT_AGENT_UID,
                "",
                "",
                "",
                "",
                ""
        );
    }

    public static DiscordBot defaultDiscordBot() {
        return new DiscordBot(
                "default",
                "Discord Default",
                false,
                true,
                true,
                List.of(),
                DEFAULT_AGENT_UID,
                "",
                "",
                "",
                "",
                false
        );
    }

    public static TelegramBot defaultTelegramBot() {
        return new TelegramBot(
                "default",
                "Telegram Default",
                false,
                true,
                true,
                List.of(),
                DEFAULT_AGENT_UID,
                "",
                "",
                "",
                ""
        );
    }

    public static QqBot defaultQqBot() {
        return new QqBot(
                "default",
                "QQ Default",
                false,
                true,
                true,
                List.of(),
                DEFAULT_AGENT_UID,
                "",
                "",
                "",
                "",
                "",
                false,
                false
        );
    }

    public static WeComBot defaultWeComBot() {
        return new WeComBot(
                "default",
                "WeCom Default",
                false,
                true,
                true,
                List.of(),
                DEFAULT_AGENT_UID,
                "",
                "",
                "",
                ""
        );
    }

    public static WeixinBot defaultWeixinBot() {
        return new WeixinBot(
                "default",
                "Weixin Default",
                false,
                true,
                true,
                List.of(),
                DEFAULT_AGENT_UID,
                "",
                "",
                "",
                "",
                ""
        );
    }

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
