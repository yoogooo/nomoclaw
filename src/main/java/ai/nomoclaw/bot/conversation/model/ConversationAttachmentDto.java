package ai.nomoclaw.bot.conversation.model;

public record ConversationAttachmentDto(
        String uploadUid,
        String name,
        String contentType,
        String mimeGroup,
        long sizeBytes,
        String fileUrl,
        boolean previewable
) {
}
