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
}
