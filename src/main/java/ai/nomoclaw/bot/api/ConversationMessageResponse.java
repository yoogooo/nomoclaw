package ai.nomoclaw.bot.api;

import ai.nomoclaw.bot.model.MessageStatus;

import java.time.Instant;
import java.util.List;

public record ConversationMessageResponse(
        String messageUid,
        String parentMessageUid,
        String role,
        String content,
        MessageStatus status,
        String provider,
        String modelName,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        Instant createdTime,
        List<MessageFileLinkResponse> fileLinks,
        List<ConversationAttachmentResponse> attachments
) {
}
