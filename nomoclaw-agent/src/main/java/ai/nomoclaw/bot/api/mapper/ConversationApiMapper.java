package ai.nomoclaw.bot.api.mapper;

import ai.nomoclaw.bot.api.dto.conversation.response.ConversationAttachmentResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessageAnchorResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessageResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessagePageResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessageRunResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationRunStepResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationSearchPageResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationSearchResultResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationSummaryResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.ConversationSummaryPageResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.LlmTraceDetailResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.LlmTraceSummaryResponse;
import ai.nomoclaw.bot.api.dto.conversation.response.MessageFileLinkResponse;
import ai.nomoclaw.bot.conversation.model.ConversationAttachmentDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageAnchorDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessagePageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageRunDto;
import ai.nomoclaw.bot.conversation.model.ConversationSearchPageDto;
import ai.nomoclaw.bot.conversation.model.ConversationSearchResultDto;
import ai.nomoclaw.bot.conversation.model.ConversationRunStepDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryPageDto;
import ai.nomoclaw.bot.conversation.model.MessageFileLinkDto;
import ai.nomoclaw.bot.conversation.model.LlmTraceDetailDto;
import ai.nomoclaw.bot.conversation.model.LlmTraceSummaryDto;

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
                dto.running(),
                dto.waitingApproval(),
                dto.unread(),
                dto.lastTaskTerminalTime(),
                dto.lastUserMessageTime(),
                dto.createdTime(),
                dto.updatedTime()
        );
    }

    public static ConversationSummaryPageResponse toConversationSummaryPage(ConversationSummaryPageDto dto) {
        return new ConversationSummaryPageResponse(
                toConversationSummaries(dto.items()),
                dto.hasMore(),
                dto.nextBeforeSortKey(),
                dto.asOf()
        );
    }

    public static ConversationSearchPageResponse toConversationSearchPage(ConversationSearchPageDto dto) {
        return new ConversationSearchPageResponse(
                dto.items().stream().map(ConversationApiMapper::toConversationSearchResult).toList(),
                dto.hasMore(),
                dto.nextBeforeSortKey()
        );
    }

    public static ConversationSearchResultResponse toConversationSearchResult(ConversationSearchResultDto dto) {
        return new ConversationSearchResultResponse(
                dto.conversationUid(),
                dto.agentGroupUid(),
                dto.agentUid(),
                dto.title(),
                dto.previewText(),
                dto.resultTime()
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
                dto.attachments().stream().map(ConversationApiMapper::toConversationAttachment).toList(),
                dto.knowledgeCitations()
        );
    }

    public static ConversationMessagePageResponse toConversationMessagePage(ConversationMessagePageDto dto) {
        return new ConversationMessagePageResponse(
                toConversationMessages(dto.items()),
                dto.hasMore(),
                dto.nextBeforeMessageUid()
        );
    }

    public static ConversationMessageAnchorResponse toConversationMessageAnchor(ConversationMessageAnchorDto dto) {
        return new ConversationMessageAnchorResponse(
                toConversationMessages(dto.items()),
                dto.hasMoreBefore(),
                dto.nextBeforeMessageUid(),
                dto.anchorMessageUid()
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

    public static List<LlmTraceSummaryResponse> toLlmTraceSummaries(List<LlmTraceSummaryDto> dtos) {
        return dtos.stream().map(ConversationApiMapper::toLlmTraceSummary).toList();
    }

    public static LlmTraceSummaryResponse toLlmTraceSummary(LlmTraceSummaryDto dto) {
        return new LlmTraceSummaryResponse(dto.traceUid(), dto.requestUid(), dto.scene(), dto.roundIndex(), dto.attemptIndex(),
                dto.provider(), dto.modelName(), dto.status(), dto.latencyMs(), dto.inputTokens(), dto.cachedInputTokens(),
                dto.outputTokens(), dto.totalTokens(), dto.usageAvailable(), dto.requestStartedTime(), dto.responseFinishedTime(),
                dto.responsePreview(), dto.errorMessage());
    }

    public static LlmTraceDetailResponse toLlmTraceDetail(LlmTraceDetailDto dto) {
        return new LlmTraceDetailResponse(dto.traceUid(), dto.requestUid(), dto.conversationUid(), dto.messageUid(), dto.scene(),
                dto.roundIndex(), dto.attemptIndex(), dto.provider(), dto.modelName(), dto.status(), dto.requestStartedTime(),
                dto.responseFinishedTime(), dto.latencyMs(), dto.systemPrompt(), dto.requestMessages(), dto.toolSpecifications(),
                dto.toolChoice(), dto.requestMetadata(), dto.protocolType(), dto.requestUrl(), dto.requestMethod(), dto.requestHeaders(),
                dto.rawRequestJson(), dto.responseStatus(), dto.rawResponseJson(), dto.rawStreamEvents(), dto.responseContent(), dto.responseThinking(), dto.responseToolCalls(), dto.finishReason(),
                dto.inputTokens(), dto.cachedInputTokens(), dto.outputTokens(), dto.totalTokens(), dto.usageAvailable(),
                dto.errorType(), dto.errorMessage());
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
