package ai.nomoclaw.bot.api;

import java.util.List;

public record UploadFilesResponse(
        List<ConversationAttachmentResponse> items
) {
}
