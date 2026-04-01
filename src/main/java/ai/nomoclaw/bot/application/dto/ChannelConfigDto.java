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
                                true,
                                List.of(),
                                "",
                                "",
                                true,
                                "OK"
                        ),
                        new DingTalk(
                                false,
                                true,
                                List.of(),
                                "",
                                "",
                                ""
                        )
                )
        );
    }

    public record Channels(
            Feishu feishu,
            DingTalk dingtalk
    ) {
    }

    public record Feishu(
            boolean enabled,
            boolean requireMention,
            List<String> allowList,
            String appId,
            String appSecret,
            boolean processingAckReactionEnabled,
            String processingAckReactionType
    ) {
    }

    public record DingTalk(
            boolean enabled,
            boolean requireMention,
            List<String> allowList,
            String clientId,
            String clientSecret,
            String robotCode
    ) {
    }
}
