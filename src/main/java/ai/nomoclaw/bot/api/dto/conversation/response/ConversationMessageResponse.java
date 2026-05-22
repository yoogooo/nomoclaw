package ai.nomoclaw.bot.api.dto.conversation.response;

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
        int cachedInputTokens,
        int outputTokens,
        int totalTokens,
        Instant createdTime,
        List<MessageFileLinkResponse> fileLinks,
        List<ConversationAttachmentResponse> attachments
) {
}
