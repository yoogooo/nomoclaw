package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.conversation.response.ConversationAttachmentResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessageResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessageRunResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationRunStepResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationSummaryResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.MessageFileLinkResponse;
import ai.nomoclaw.bot.conversation.model.ConversationAttachmentDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageRunDto;
import ai.nomoclaw.bot.conversation.model.ConversationRunStepDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryDto;
import ai.nomoclaw.bot.conversation.model.MessageFileLinkDto;

import java.util.List;

/**
 * Maps conversation-layer DTOs to API payloads.
 */
public final class ConversationApiMapper {

    private ConversationApiMapper() {
    }

    public static List<ConversationSummaryResponse> toConversationSummaries(List<ConversationSummaryDto> dtos) {
        return dtos.stream().map(ConversationApiMapper::toConversationSummary).toList();
    }

    public static ConversationSummaryResponse toConversationSummary(ConversationSummaryDto dto) {
        return new ConversationSummaryResponse(
                dto.conversationUid(),
                dto.agentGroupUid(),
                dto.agentUid(),
                dto.title(),
                dto.pinned(),
                dto.waitingApproval(),
                dto.unread(),
                dto.lastTaskTerminalTime(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static List<ConversationMessageResponse> toConversationMessages(List<ConversationMessageDto> dtos) {
        return dtos.stream().map(ConversationApiMapper::toConversationMessage).toList();
    }

    public static ConversationMessageResponse toConversationMessage(ConversationMessageDto dto) {
        return new ConversationMessageResponse(
                dto.messageUid(),
                dto.parentMessageUid(),
                dto.role(),
                dto.content(),
                dto.status(),
                dto.provider(),
                dto.modelName(),
                dto.inputTokens(),
                dto.cachedInputTokens(),
                dto.outputTokens(),
                dto.totalTokens(),
                dto.createdTime(),
                dto.fileLinks().stream().map(ConversationApiMapper::toMessageFileLink).toList(),
                dto.attachments().stream().map(ConversationApiMapper::toConversationAttachment).toList()
        );
    }

    public static MessageFileLinkResponse toMessageFileLink(MessageFileLinkDto dto) {
        return new MessageFileLinkResponse(dto.name(), dto.path());
    }

    public static ConversationAttachmentResponse toConversationAttachment(ConversationAttachmentDto dto) {
        return new ConversationAttachmentResponse(
                dto.uploadUid(),
                dto.name(),
                dto.contentType(),
                dto.mimeGroup(),
                dto.sizeBytes(),
                dto.fileUrl(),
                dto.previewable()
        );
    }

    public static List<ConversationMessageRunResponse> toMessageRuns(List<ConversationMessageRunDto> dtos) {
        return dtos.stream().map(ConversationApiMapper::toMessageRun).toList();
    }

    public static ConversationMessageRunResponse toMessageRun(ConversationMessageRunDto dto) {
        return new ConversationMessageRunResponse(
                dto.messageUid(),
                dto.status(),
                dto.summary(),
                dto.completedSteps(),
                dto.totalSteps(),
                dto.updatedTime(),
                dto.steps().stream().map(ConversationApiMapper::toRunStep).toList()
        );
    }

    public static ConversationRunStepResponse toRunStep(ConversationRunStepDto dto) {
        return new ConversationRunStepResponse(
                dto.stepUid(),
                dto.roundIndex(),
                dto.stepIndex(),
                dto.status(),
                dto.toolName(),
                dto.toolArgs(),
                dto.displayTitle(),
                dto.displaySummary(),
                dto.displayDetails(),
                dto.policyReasonCode(),
                dto.updatedTime()
        );
    }
}
