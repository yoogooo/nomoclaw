package ai.nomoclaw.bot.application.dto;

import ai.nomoclaw.bot.model.MessageStatus;

import java.time.Instant;
import java.util.List;

public record ConversationMessageDto(
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
        List<MessageFileLinkDto> fileLinks,
        List<ConversationAttachmentDto> attachments
) {
}
