package ai.nomoclaw.bot.api.dto.conversation.response;

import java.util.List;

public record UploadFilesResponse(
        List<ConversationAttachmentResponse> items
) {
}
