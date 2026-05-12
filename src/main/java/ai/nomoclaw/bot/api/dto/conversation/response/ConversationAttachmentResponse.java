package ai.nomoclaw.bot.api.dto.conversation.response;

public record ConversationAttachmentResponse(
        String uploadUid,
        String name,
        String contentType,
        String mimeGroup,
        long sizeBytes,
        String fileUrl,
        boolean previewable
) {
}
