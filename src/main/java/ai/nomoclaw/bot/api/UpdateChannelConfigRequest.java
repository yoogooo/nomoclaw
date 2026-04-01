package ai.nomoclaw.bot.api;

import java.util.List;

public record UpdateChannelConfigRequest(
        Channels channels
) {

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
