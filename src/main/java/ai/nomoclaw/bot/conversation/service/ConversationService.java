package ai.nomoclaw.bot.conversation.service;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.conversation.model.ConversationAttachmentDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageAnchorDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessagePageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageRunDto;
import ai.nomoclaw.bot.conversation.model.ConversationSearchPageDto;
import ai.nomoclaw.bot.conversation.model.ConversationSearchResultDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryPageDto;
import ai.nomoclaw.bot.conversation.model.MessageFileLinkDto;
import ai.nomoclaw.bot.conversation.support.ConversationAttachmentService;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.AgentEvent;
import ai.nomoclaw.bot.model.AgentEventType;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScopeResolver;
import ai.nomoclaw.bot.orchestrator.execution.MessageExecutionOrchestrator;
import ai.nomoclaw.bot.orchestrator.execution.RuntimeModelSelection;
import ai.nomoclaw.bot.orchestrator.view.RunViewAssembler;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.query.ConversationPageQuery;
import ai.nomoclaw.bot.store.query.ConversationSearchQuery;
import ai.nomoclaw.bot.store.query.ConversationSearchRow;
import ai.nomoclaw.bot.store.query.MessagePageQuery;
import ai.nomoclaw.bot.store.query.PageSlice;
import ai.nomoclaw.bot.util.UuidUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 会话领域服务，统一承载会话与消息的读写能力。
 */
@Service
@Slf4j
public class ConversationService {

    public static final String APPROVAL_MODE_DEFAULT = "default";
    private static final String APPROVAL_MODE_FULL_ACCESS = "full_access";
    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";
    private static final int DEFAULT_PAGE_LIMIT = 20;
    private static final int MAX_PAGE_LIMIT = 50;
    private static final int DEFAULT_SEARCH_LIMIT = 50;
    private static final int MAX_MESSAGE_ANCHOR_LIMIT = 100;

    private final AgentStore store;
    private final AgentProperties properties;
    private final MessageCancellationRegistry cancellationRegistry;
    private final MessageExecutionOrchestrator messageExecutionOrchestrator;
    private final ExecutionScopeResolver executionScopeResolver;
    private final ConversationAttachmentService conversationAttachmentService;
    private final RunViewAssembler runViewAssembler;

    public ConversationService(AgentStore store,
                               AgentProperties properties,
                               MessageCancellationRegistry cancellationRegistry,
                               MessageExecutionOrchestrator messageExecutionOrchestrator,
                               ExecutionScopeResolver executionScopeResolver,
                               ConversationAttachmentService conversationAttachmentService,
                               RunViewAssembler runViewAssembler) {
        this.store = store;
        this.properties = properties;
        this.cancellationRegistry = cancellationRegistry;
        this.messageExecutionOrchestrator = messageExecutionOrchestrator;
        this.executionScopeResolver = executionScopeResolver;
        this.conversationAttachmentService = conversationAttachmentService;
        this.runViewAssembler = runViewAssembler;
    }

    public String createConversation(String agentGroupUid, String agentUid, String channel) {
        String conversationUid = UuidUtil.newUuid();
        String normalizedGroupUid = normalizeAgentGroupUid(agentGroupUid);
        String normalizedAgentUid = normalizeOptionalAgentUid(agentUid);
        String normalizedChannel = channel == null || channel.isBlank() ? "web" : channel.trim();
        store.createConversation(
                conversationUid,
                normalizedGroupUid,
                normalizedAgentUid,
                normalizedChannel
        );
        log.info("[Agent] conversation created conversationUid={} agentGroupUid={} agentUid={} channel={}",
                conversationUid, normalizedGroupUid, normalizedAgentUid, normalizedChannel);
        return conversationUid;
    }

    public List<ConversationSummaryDto> listConversations() {
        return store.listConversations().stream()
                .map(this::toConversationSummary)
                .sorted(Comparator
                        .comparing(ConversationSummaryDto::pinned)
                        .reversed()
                        .thenComparing(ConversationSummaryDto::lastUserMessageTime, Comparator.reverseOrder())
                        .thenComparing(ConversationSummaryDto::conversationUid, Comparator.reverseOrder()))
                .toList();
    }

    public ConversationSummaryPageDto listConversationPage(String agentUid, Integer limit, String beforeSortKey, String asOf) {
        int normalizedLimit = normalizePageLimit(limit);
        Instant snapshotTime = parseAsOf(asOf);
        ConversationSortCursor cursor = parseConversationSortCursor(beforeSortKey);
        PageSlice<AgentConversation> page = store.listConversationPage(new ConversationPageQuery(
                normalizeOptionalAgentUid(agentUid),
                normalizedLimit,
                snapshotTime,
                cursor == null ? null : cursor.pinned(),
                cursor == null ? null : cursor.lastUserMessageTime(),
                cursor == null ? null : cursor.id()
        ));
        List<ConversationSummaryDto> items = page.items().stream()
                .map(this::toConversationSummary)
                .toList();
        String nextBeforeSortKey = null;
        if (page.hasMore() && !page.items().isEmpty()) {
            nextBeforeSortKey = encodeConversationSortCursor(items.get(items.size() - 1));
        }
        return new ConversationSummaryPageDto(items, page.hasMore(), nextBeforeSortKey, snapshotTime);
    }

    public ConversationSearchPageDto searchConversationPage(String agentUid, String keyword, Integer limit, String beforeSortKey) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedLimit = normalizeSearchLimit(limit);
        SearchSortCursor cursor = parseSearchSortCursor(beforeSortKey);
        PageSlice<ConversationSearchRow> page = store.searchConversationPage(new ConversationSearchQuery(
                normalizeOptionalAgentUid(agentUid),
                "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%",
                normalizedLimit,
                cursor == null ? null : cursor.resultTime(),
                cursor == null ? null : cursor.conversationId()
        ));
        List<ConversationSearchResultDto> items = page.items().stream()
                .map(this::toConversationSearchResult)
                .toList();
        String nextBeforeSortKey = null;
        if (page.hasMore() && !page.items().isEmpty()) {
            nextBeforeSortKey = encodeSearchSortCursor(page.items().get(page.items().size() - 1));
        }
        return new ConversationSearchPageDto(items, page.hasMore(), nextBeforeSortKey);
    }

    private boolean isRunning(AgentMessage latestUserMessage) {
        if (latestUserMessage == null) {
            return false;
        }
        return switch (latestUserMessage.status()) {
            case CREATED, PLANNED, RUNNING, REPLANNING -> true;
            default -> false;
        };
    }

    public List<ConversationMessageDto> listMessages(String conversationUid) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        List<AgentMessage> messages = store.listMessagesByConversation(conversationUid);
        return toConversationMessages(messages);
    }

    public ConversationMessagePageDto listMessagePage(String conversationUid, Integer limit, String beforeMessageUid) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        int normalizedLimit = normalizePageLimit(limit);
        Long beforeId = null;
        if (beforeMessageUid != null && !beforeMessageUid.isBlank()) {
            AgentMessage beforeMessage = store.findMessage(beforeMessageUid)
                    .orElseThrow(() -> new IllegalArgumentException("message not found: " + beforeMessageUid));
            if (!conversationUid.equals(beforeMessage.conversationUid())) {
                throw new IllegalArgumentException("message does not belong to conversation: " + beforeMessageUid);
            }
            beforeId = store.findMessageSortId(beforeMessageUid)
                    .orElseThrow(() -> new IllegalArgumentException("message not found: " + beforeMessageUid));
        }
        PageSlice<AgentMessage> page = store.listMessagePage(new MessagePageQuery(conversationUid, normalizedLimit, beforeId));
        List<AgentMessage> ascendingMessages = new ArrayList<>(page.items());
        Collections.reverse(ascendingMessages);
        String nextBeforeMessageUid = null;
        if (page.hasMore() && !ascendingMessages.isEmpty()) {
            nextBeforeMessageUid = ascendingMessages.get(0).messageUid();
        }
        return new ConversationMessagePageDto(
                toConversationMessages(ascendingMessages),
                page.hasMore(),
                nextBeforeMessageUid
        );
    }

    public ConversationMessageAnchorDto loadMessageAnchor(String conversationUid, String keyword, Integer beforeLimit, Integer afterLimit) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        String normalizedKeyword = normalizeKeyword(keyword);
        AgentMessage anchorMessage = store.findLatestMatchingMessage(
                        conversationUid,
                        "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%"
                )
                .orElse(null);
        if (anchorMessage == null) {
            return new ConversationMessageAnchorDto(List.of(), false, null, null);
        }
        Long anchorId = store.findMessageSortId(anchorMessage.messageUid())
                .orElseThrow(() -> new IllegalArgumentException("message not found: " + anchorMessage.messageUid()));
        int normalizedBeforeLimit = normalizeAnchorLimit(beforeLimit, 30);
        int normalizedAfterLimit = normalizeAnchorLimit(afterLimit, 20);
        List<AgentMessage> beforeOrAt = store.listMessagesBeforeOrAt(conversationUid, anchorId, normalizedBeforeLimit + 2);
        boolean hasMoreBefore = beforeOrAt.size() > normalizedBeforeLimit + 1;
        if (hasMoreBefore) {
            beforeOrAt = beforeOrAt.subList(0, normalizedBeforeLimit + 1);
        }
        List<AgentMessage> ascendingBeforeOrAt = new ArrayList<>(beforeOrAt);
        Collections.reverse(ascendingBeforeOrAt);
        List<AgentMessage> after = store.listMessagesAfter(conversationUid, anchorId, normalizedAfterLimit);
        List<AgentMessage> merged = new ArrayList<>(ascendingBeforeOrAt);
        merged.addAll(after);
        String nextBeforeMessageUid = hasMoreBefore && !ascendingBeforeOrAt.isEmpty()
                ? ascendingBeforeOrAt.get(0).messageUid()
                : null;
        return new ConversationMessageAnchorDto(
                toConversationMessages(merged),
                hasMoreBefore,
                nextBeforeMessageUid,
                anchorMessage.messageUid()
        );
    }

    private List<ConversationMessageDto> toConversationMessages(List<AgentMessage> messages) {
        Map<String, List<ConversationAttachmentDto>> attachmentsByMessage = conversationAttachmentService.listByMessageUids(
                messages.stream().map(AgentMessage::messageUid).toList()
        );
        return messages.stream()
                .map(message -> new ConversationMessageDto(
                        message.messageUid(),
                        emptyToNull(message.parentMessageUid()),
                        message.role(),
                        message.content(),
                        message.status(),
                        message.provider(),
                        message.modelName(),
                        message.inputTokens(),
                        message.cachedInputTokens(),
                        message.outputTokens(),
                        message.totalTokens(),
                        message.createdAt(),
                        buildMessageFileLinks(message),
                        attachmentsByMessage.getOrDefault(message.messageUid(), List.of())
                ))
                .toList();
    }

    public List<ConversationMessageRunDto> listMessageRuns(String conversationUid) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        return store.listMessagesByConversation(conversationUid).stream()
                .filter(message -> "user".equals(message.role()))
                .map(message -> runViewAssembler.toMessageRunResponse(
                        message,
                        store.listSteps(message.messageUid()),
                        store.listEventsByMessage(message.messageUid())
                ))
                .filter(Objects::nonNull)
                .toList();
    }

    private ConversationSummaryDto toConversationSummary(AgentConversation conversation) {
        AgentMessage latestUserMessage = store.findLatestUserMessageByConversation(conversation.conversationUid()).orElse(null);
        boolean waitingApproval = latestUserMessage != null && latestUserMessage.status() == MessageStatus.WAITING_APPROVAL;
        boolean running = isRunning(latestUserMessage);
        boolean unread = isUnread(conversation)
                || isWaitingApprovalUnread(conversation, latestUserMessage, waitingApproval);
        return new ConversationSummaryDto(
                conversation.conversationUid(),
                conversation.agentGroupUid(),
                conversation.agentUid(),
                conversation.title(),
                conversation.pinned(),
                running,
                waitingApproval,
                unread,
                conversation.lastTaskTerminalAt(),
                conversation.lastUserMessageAt(),
                conversation.createdAt(),
                conversation.updatedAt()
        );
    }

    public AgentConversation getConversation(String conversationUid) {
        return store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
    }

    public void deleteConversation(String conversationUid) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        List<AgentMessage> messages = store.listMessagesByConversation(conversationUid);
        messages.forEach(message -> {
            cancellationRegistry.cancel(message.messageUid());
            messageExecutionOrchestrator.clearRuntime(message.messageUid());
        });
        conversationAttachmentService.purgeConversationAttachments(conversationUid);
        store.deleteConversation(conversationUid);
        log.info("[Agent] conversation deleted conversationUid={} agentGroupUid={} agentUid={}",
                conversationUid, conversation.agentGroupUid(), conversation.agentUid());
    }

    public void updateConversationTitle(String conversationUid, String title) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        store.updateConversationTitle(conversationUid, normalizedTitle);
        log.info("[Agent] conversation title updated conversationUid={} title={} agentGroupUid={} agentUid={}",
                conversationUid, normalizedTitle, conversation.agentGroupUid(), conversation.agentUid());
    }

    public void updateConversationPinned(String conversationUid, boolean pinned) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        store.updateConversationPinned(conversationUid, pinned);
        log.info("[Agent] conversation pin updated conversationUid={} pinned={} agentGroupUid={} agentUid={}",
                conversationUid, pinned, conversation.agentGroupUid(), conversation.agentUid());
    }

    public void markConversationRead(String conversationUid) {
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        AgentMessage latestUserMessage = store.findLatestUserMessageByConversation(conversationUid).orElse(null);
        if (latestUserMessage == null || latestUserMessage.updatedAt() == null) {
            return;
        }
        Instant lastReadAt = conversation.lastReadAt();
        if (lastReadAt != null && !latestUserMessage.updatedAt().isAfter(lastReadAt)) {
            return;
        }
        store.markConversationRead(conversationUid, Instant.now());
    }

    public String submitMessage(String conversationUid,
                                String message,
                                List<String> fileUrls,
                                String modelProvider,
                                String modelName,
                                String approvalMode,
                                String channel,
                                Consumer<String> beforeExecuteHook,
                                MessageExecutionOrchestrator.ExecutionDriver executionDriver) {
        String normalizedChannel = channel == null || channel.isBlank() ? "web" : channel.trim();
        String normalizedApprovalMode = normalizeApprovalMode(approvalMode);
        AgentConversation conversation = store.findConversation(conversationUid)
                .orElseGet(() -> store.createConversation(conversationUid, "", DEFAULT_AGENT_UID, normalizedChannel));
        RuntimeModelSelection runtimeModel = executionScopeResolver.resolveForMessageSubmission(conversation, modelProvider, modelName);
        String messageUid = UuidUtil.newUuid();
        int maxRounds = properties.getLoop().getMaxRounds();
        store.createUserMessage(
                messageUid,
                conversationUid,
                message,
                maxRounds,
                runtimeModel.modelProvider(),
                runtimeModel.modelName()
        );
        conversationAttachmentService.attachUploadsToMessage(
                conversationUid,
                messageUid,
                fileUrls,
                runtimeModel.modelProvider(),
                runtimeModel.modelName()
        );
        if (conversation.title() == null || conversation.title().isBlank()) {
            store.updateConversationTitle(conversationUid, buildConversationTitle(message));
        }
        log.info("[Agent] message created conversationUid={} messageUid={} channel={} model={}/{} maxRounds={} message={}",
                conversationUid, messageUid, normalizedChannel, runtimeModel.modelProvider(), runtimeModel.modelName(), maxRounds, summarize(message));
        if (beforeExecuteHook != null) {
            beforeExecuteHook.accept(messageUid);
        }
        messageExecutionOrchestrator.enqueue(messageUid, conversationUid, resolveRequestLocale(), normalizedApprovalMode, executionDriver);
        return messageUid;
    }

    public AgentMessage getMessage(String messageUid) {
        return store.findMessage(messageUid)
                .orElseThrow(() -> new IllegalArgumentException("message not found: " + messageUid));
    }

    public String updateApprovalMode(String conversationUid, String approvalMode, boolean applyToRunning) {
        store.findConversation(conversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + conversationUid));
        String normalized = normalizeApprovalMode(approvalMode);
        if (applyToRunning) {
            messageExecutionOrchestrator.updateApprovalModeForConversation(conversationUid, normalized);
        }
        return normalized;
    }

    public int maxLoopRounds() {
        return properties.getLoop().getMaxRounds();
    }

    public String normalizeApprovalMode(String approvalMode) {
        String normalized = approvalMode == null ? "" : approvalMode.trim().toLowerCase();
        return APPROVAL_MODE_FULL_ACCESS.equals(normalized) ? APPROVAL_MODE_FULL_ACCESS : APPROVAL_MODE_DEFAULT;
    }

    private List<MessageFileLinkDto> buildMessageFileLinks(AgentMessage message) {
        if (!"assistant".equals(message.role()) || message.parentMessageUid() == null || message.parentMessageUid().isBlank()) {
            return List.of();
        }
        Map<String, MessageFileLinkDto> files = new LinkedHashMap<>();
        for (AgentEvent event : store.listEventsByMessage(message.parentMessageUid())) {
            if (event.eventType() != AgentEventType.STEP_FINISHED || event.payload() == null || !event.payload().path("success").asBoolean(false)) {
                continue;
            }
            String path = event.payload().path("artifacts").path("path").asString("");
            if (path.isBlank()) {
                continue;
            }
            Path filePath = Path.of(path).toAbsolutePath().normalize();
            if (!Files.isRegularFile(filePath)) {
                continue;
            }
            String key = filePath.toString();
            files.putIfAbsent(key, new MessageFileLinkDto(filePath.getFileName().toString(), key));
        }
        return List.copyOf(files.values());
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean isUnread(AgentConversation conversation) {
        if (conversation.lastTaskTerminalAt() == null) {
            return false;
        }
        if (conversation.lastReadAt() == null) {
            return true;
        }
        return conversation.lastTaskTerminalAt().isAfter(conversation.lastReadAt());
    }

    private boolean isWaitingApprovalUnread(AgentConversation conversation,
                                            AgentMessage latestUserMessage,
                                            boolean waitingApproval) {
        if (!waitingApproval || latestUserMessage == null) {
            return false;
        }
        if (conversation.lastReadAt() == null) {
            return true;
        }
        return latestUserMessage.updatedAt().isAfter(conversation.lastReadAt());
    }

    private String buildConversationTitle(String message) {
        if (message == null || message.isBlank()) {
            return "未命名对话";
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 24) {
            return normalized;
        }
        return normalized.substring(0, 24) + "...";
    }

    private ConversationSearchResultDto toConversationSearchResult(ConversationSearchRow row) {
        return new ConversationSearchResultDto(
                row.getConversationUid(),
                row.getAgentGroupUid(),
                row.getAgentUid(),
                emptyToNull(row.getTitle()) == null ? "未命名对话" : row.getTitle(),
                summarizePreview(row.getPreviewText(), row.getTitle()),
                toInstant(row.getResultTime())
        );
    }

    private String normalizeAgentGroupUid(String agentGroupUid) {
        return agentGroupUid == null || agentGroupUid.isBlank() ? "" : agentGroupUid.trim();
    }

    private String normalizeOptionalAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? "" : agentUid.trim();
    }

    private int normalizePageLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_LIMIT;
        }
        return Math.min(limit, MAX_PAGE_LIMIT);
    }

    private int normalizeSearchLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_PAGE_LIMIT);
    }

    private int normalizeAnchorLimit(Integer limit, int defaultLimit) {
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, MAX_MESSAGE_ANCHOR_LIMIT);
    }

    private String normalizeKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        return normalized;
    }

    private Instant parseAsOf(String asOf) {
        if (asOf == null || asOf.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(asOf.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid asOf: " + asOf, ex);
        }
    }

    private String encodeConversationSortCursor(ConversationSummaryDto summary) {
        Long sortId = store.findConversationSortId(summary.conversationUid())
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + summary.conversationUid()));
        String raw = (summary.pinned() ? 1 : 0)
                + "|"
                + summary.lastUserMessageTime().toEpochMilli()
                + "|"
                + sortId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ConversationSortCursor parseConversationSortCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid beforeSortKey");
            }
            return new ConversationSortCursor(
                    Integer.parseInt(parts[0]),
                    Instant.ofEpochMilli(Long.parseLong(parts[1])),
                    Long.parseLong(parts[2])
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid beforeSortKey", ex);
        }
    }

    private String encodeSearchSortCursor(ConversationSearchRow row) {
        String raw = toInstant(row.getResultTime()).toEpochMilli()
                + "|"
                + row.getConversationId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private SearchSortCursor parseSearchSortCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid beforeSortKey");
            }
            return new SearchSortCursor(
                    Instant.ofEpochMilli(Long.parseLong(parts[0])),
                    Long.parseLong(parts[1])
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid beforeSortKey", ex);
        }
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        int limit = 2000;
        return text.length() <= limit ? text : text.substring(0, limit) + "...";
    }

    private String summarizePreview(String previewText, String title) {
        String raw = previewText == null ? "" : previewText;
        String normalized = raw.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        int limit = 120;
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit) + "...";
    }

    private Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Locale resolveRequestLocale() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null || attributes.getRequest() == null) {
            return LocaleContextHolder.getLocale();
        }
        String explicitLocale = attributes.getRequest().getHeader("X-App-Locale");
        Locale parsedExplicit = parseLocaleHeader(explicitLocale);
        if (parsedExplicit != null) {
            return parsedExplicit;
        }
        String acceptLanguage = attributes.getRequest().getHeader("Accept-Language");
        Locale parsedAcceptLanguage = parseAcceptLanguageHeader(acceptLanguage);
        if (parsedAcceptLanguage != null) {
            return parsedAcceptLanguage;
        }
        return LocaleContextHolder.getLocale();
    }

    private Locale parseLocaleHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replace('_', '-');
        Locale locale = Locale.forLanguageTag(normalized);
        if (locale.getLanguage() == null || locale.getLanguage().isBlank()) {
            return null;
        }
        return locale;
    }

    private Locale parseAcceptLanguageHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String firstLanguageRange = raw.split(",")[0].trim();
        if (firstLanguageRange.isBlank()) {
            return null;
        }
        String tag = firstLanguageRange.split(";")[0].trim();
        if (tag.isBlank()) {
            return null;
        }
        return parseLocaleHeader(tag);
    }

    private record ConversationSortCursor(
            Integer pinned,
            Instant lastUserMessageTime,
            Long id
    ) {
    }

    private record SearchSortCursor(
            Instant resultTime,
            Long conversationId
    ) {
    }
}
