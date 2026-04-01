package ai.nomoclaw.bot.api;

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
