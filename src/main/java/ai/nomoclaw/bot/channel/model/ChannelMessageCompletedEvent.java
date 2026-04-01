package ai.nomoclaw.bot.channel.model;

import ai.nomoclaw.bot.model.MessageStatus;

public record ChannelMessageCompletedEvent(
        String messageUid,
        String conversationUid,
        MessageStatus status,
        String finalReply,
        String channel,
        String replyTarget
) {
}
