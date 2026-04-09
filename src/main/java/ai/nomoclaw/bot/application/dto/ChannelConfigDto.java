package ai.nomoclaw.bot.application.dto;

import java.util.List;

public record ChannelConfigDto(
        Channels channels
) {

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
                "",
                "",
                true,
                "OK"
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
                "",
                "",
                ""
        );
    }

    public record Channels(
            Feishu feishu,
            DingTalk dingtalk
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
            String processingAckReactionType
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
}
