package ai.nomoclaw.bot.store;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.model.AgentEvent;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.AgentEventType;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.RiskLevel;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.store.query.ConversationPageQuery;
import ai.nomoclaw.bot.store.query.ConversationSearchQuery;
import ai.nomoclaw.bot.store.query.ConversationSearchRow;
import ai.nomoclaw.bot.store.query.MessagePageQuery;
import ai.nomoclaw.bot.store.query.PageSlice;
import ai.nomoclaw.bot.store.entity.AgentEventEntity;
import ai.nomoclaw.bot.store.entity.AgentMessageEntity;
import ai.nomoclaw.bot.store.entity.AgentConversationEntity;
import ai.nomoclaw.bot.store.entity.AgentStepEntity;
import ai.nomoclaw.bot.store.repository.AgentEventRepository;
import ai.nomoclaw.bot.store.repository.AgentMessageRepository;
import ai.nomoclaw.bot.store.repository.AgentConversationRepository;
import ai.nomoclaw.bot.store.repository.AgentStepRepository;
import ai.nomoclaw.bot.util.JsonUtil;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public class MybatisPlusAgentStore implements AgentStore {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();
    private static final int MAX_STEP_TITLE_LENGTH = 255;
    private static final int MAX_STEP_DONE_CRITERIA_LENGTH = 512;

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentStepRepository stepRepository;
    private final AgentEventRepository eventRepository;

    public MybatisPlusAgentStore(AgentConversationRepository conversationRepository,
                                 AgentMessageRepository messageRepository,
                                 AgentStepRepository stepRepository,
                                 AgentEventRepository eventRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public AgentConversation createConversation(String conversationUid, String agentGroupUid, String agentUid) {
        return createConversation(conversationUid, agentGroupUid, agentUid, "web");
    }

    @Override
    public AgentConversation createConversation(String conversationUid, String agentGroupUid, String agentUid, String channel) {
        Instant now = Instant.now();
        AgentConversationEntity entity = new AgentConversationEntity();
        entity.setConversationUid(conversationUid);
        entity.setAgentGroupUid(agentGroupUid);
        entity.setAgentUid(agentUid);
        entity.setChannel(channel == null || channel.isBlank() ? "web" : channel.trim());
        entity.setTitle("");
        entity.setPinned(false);
        entity.setInputTokens(0);
        entity.setCachedInputTokens(0);
        entity.setOutputTokens(0);
        entity.setTotalTokens(0);
        entity.setLastTaskTerminalTime(null);
        entity.setLastReadAt(toLocalDateTime(now));
        entity.setLastUserMessageTime(toLocalDateTime(now));
        entity.setCreatedTime(toLocalDateTime(now));
        entity.setUpdatedTime(toLocalDateTime(now));
        conversationRepository.save(entity);
        return new AgentConversation(conversationUid, agentGroupUid, agentUid, entity.getChannel(), "", false, 0, 0, 0, 0, null, now, now, now, now);
    }

    @Override
    public List<AgentConversation> listConversations() {
        return conversationRepository.listAllDesc()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PageSlice<AgentConversation> listConversationPage(ConversationPageQuery query) {
        List<AgentConversationEntity> entities = conversationRepository.listConversationPage(
                query.agentUid(),
                toLocalDateTime(query.asOf()),
                query.beforePinned(),
                query.beforeLastUserMessageTime() == null ? null : toLocalDateTime(query.beforeLastUserMessageTime()),
                query.beforeId(),
                query.limit() + 1
        );
        boolean hasMore = entities.size() > query.limit();
        if (hasMore) {
            entities = entities.subList(0, query.limit());
        }
        return new PageSlice<>(
                entities.stream().map(this::toDomain).toList(),
                hasMore
        );
    }

    @Override
    public PageSlice<ConversationSearchRow> searchConversationPage(ConversationSearchQuery query) {
        List<ConversationSearchRow> rows = conversationRepository.searchConversationPage(
                query.agentUid(),
                query.keywordPattern(),
                query.beforeResultTime() == null ? null : toLocalDateTime(query.beforeResultTime()),
                query.beforeConversationId(),
                query.limit() + 1
        );
        boolean hasMore = rows.size() > query.limit();
        if (hasMore) {
            rows = rows.subList(0, query.limit());
        }
        return new PageSlice<>(rows, hasMore);
    }

    @Override
    public Optional<AgentConversation> findConversation(String conversationUid) {
        AgentConversationEntity entity = conversationRepository.findByConversationUid(conversationUid);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<Long> findConversationSortId(String conversationUid) {
        return Optional.ofNullable(conversationRepository.findSortIdByConversationUid(conversationUid));
    }

    @Override
    public void deleteConversation(String conversationUid) {
        eventRepository.deleteByConversationUid(conversationUid);
        stepRepository.deleteByConversationUid(conversationUid);
        messageRepository.deleteByConversationUid(conversationUid);
        conversationRepository.deleteByConversationUid(conversationUid);
    }

    @Override
    public void touchConversation(String conversationUid) {
        conversationRepository.update(new LambdaUpdateWrapper<AgentConversationEntity>()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .set(AgentConversationEntity::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    public void updateConversationTitle(String conversationUid, String title) {
        conversationRepository.update(new LambdaUpdateWrapper<AgentConversationEntity>()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .set(AgentConversationEntity::getTitle, title)
                .set(AgentConversationEntity::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    public void updateConversationPinned(String conversationUid, boolean pinned) {
        conversationRepository.update(new LambdaUpdateWrapper<AgentConversationEntity>()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .set(AgentConversationEntity::getPinned, pinned));
    }

    @Override
    public void markConversationRead(String conversationUid, Instant readAt) {
        if (conversationUid == null || conversationUid.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime readAtTime = readAt == null ? now : toLocalDateTime(readAt);
        conversationRepository.update(new LambdaUpdateWrapper<AgentConversationEntity>()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .set(AgentConversationEntity::getLastReadAt, readAtTime)
                .set(AgentConversationEntity::getUpdatedTime, now));
    }

    @Override
    public AgentMessage createUserMessage(String messageUid,
                                          String conversationUid,
                                          String content,
                                          int maxRounds,
                                          String provider,
                                          String modelName) {
        Instant now = Instant.now();
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setMessageUid(messageUid);
        entity.setConversationUid(conversationUid);
        entity.setParentMessageUid("");
        entity.setRole("user");
        entity.setContent(content);
        entity.setStatus(MessageStatus.CREATED.name());
        entity.setProvider(provider == null ? "" : provider.trim());
        entity.setModelName(modelName == null ? "" : modelName.trim());
        entity.setInputTokens(0);
        entity.setCachedInputTokens(0);
        entity.setOutputTokens(0);
        entity.setTotalTokens(0);
        entity.setCreatedTime(toLocalDateTime(now));
        entity.setUpdatedTime(toLocalDateTime(now));
        messageRepository.save(entity);
        conversationRepository.update(new LambdaUpdateWrapper<AgentConversationEntity>()
                .eq(AgentConversationEntity::getConversationUid, conversationUid)
                .set(AgentConversationEntity::getLastUserMessageTime, toLocalDateTime(now))
                .set(AgentConversationEntity::getUpdatedTime, toLocalDateTime(now)));
        return toDomain(entity);
    }

    @Override
    public AgentMessage createAssistantMessage(String messageUid, String conversationUid, String parentMessageUid, String content) {
        Instant now = Instant.now();
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setMessageUid(messageUid);
        entity.setConversationUid(conversationUid);
        entity.setParentMessageUid(parentMessageUid == null ? "" : parentMessageUid);
        entity.setRole("assistant");
        entity.setContent(content);
        entity.setStatus(MessageStatus.COMPLETED.name());
        entity.setProvider("");
        entity.setModelName("");
        entity.setInputTokens(0);
        entity.setCachedInputTokens(0);
        entity.setOutputTokens(0);
        entity.setTotalTokens(0);
        entity.setCreatedTime(toLocalDateTime(now));
        entity.setUpdatedTime(toLocalDateTime(now));
        messageRepository.save(entity);
        touchConversation(conversationUid);
        return toDomain(entity);
    }

    @Override
    public Optional<AgentMessage> findMessage(String messageUid) {
        AgentMessageEntity entity = messageRepository.findByMessageId(messageUid);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<Long> findMessageSortId(String messageUid) {
        return Optional.ofNullable(messageRepository.findSortIdByMessageUid(messageUid));
    }

    @Override
    public List<AgentMessage> listMessagesByConversation(String conversationUid) {
        return messageRepository.listByConversation(conversationUid)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<AgentMessage> findLatestMatchingMessage(String conversationUid, String keywordPattern) {
        return Optional.ofNullable(messageRepository.findLatestMatchingMessage(conversationUid, keywordPattern))
                .map(this::toDomain);
    }

    @Override
    public List<AgentMessage> listMessagesBeforeOrAt(String conversationUid, long messageSortId, int limit) {
        return messageRepository.listBeforeOrAt(conversationUid, messageSortId, limit)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<AgentMessage> listMessagesAfter(String conversationUid, long messageSortId, int limit) {
        return messageRepository.listAfter(conversationUid, messageSortId, limit)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PageSlice<AgentMessage> listMessagePage(MessagePageQuery query) {
        List<AgentMessageEntity> entities = messageRepository.listPageByConversation(
                query.conversationUid(),
                query.beforeId(),
                query.limit() + 1
        );
        boolean hasMore = entities.size() > query.limit();
        if (hasMore) {
            entities = entities.subList(0, query.limit());
        }
        return new PageSlice<>(
                entities.stream().map(this::toDomain).toList(),
                hasMore
        );
    }

    @Override
    public Optional<AgentMessage> findLatestUserMessageByConversation(String conversationUid) {
        AgentMessageEntity entity = messageRepository.findLatestUserMessageByConversation(conversationUid);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public boolean hasInProgressUserMessage(String conversationUid) {
        if (conversationUid == null || conversationUid.isBlank()) {
            return false;
        }
        AgentMessageEntity entity = messageRepository.findLatestInProgressUserMessageByConversation(conversationUid);
        return entity != null;
    }

    @Override
    public void updateMessageStatus(String messageUid, MessageStatus status) {
        LocalDateTime now = LocalDateTime.now();
        boolean updated = messageRepository.update(new LambdaUpdateWrapper<AgentMessageEntity>()
                .eq(AgentMessageEntity::getMessageUid, messageUid)
                .notIn(AgentMessageEntity::getStatus,
                        MessageStatus.COMPLETED.name(),
                        MessageStatus.FAILED.name(),
                        MessageStatus.CANCELED.name())
                .set(AgentMessageEntity::getStatus, status.name())
                .set(AgentMessageEntity::getUpdatedTime, now));
        if (status == MessageStatus.COMPLETED || status == MessageStatus.FAILED || status == MessageStatus.CANCELED) {
            AgentMessageEntity messageEntity = messageRepository.findByMessageId(messageUid);
            if (updated && messageEntity != null && messageEntity.getConversationUid() != null && !messageEntity.getConversationUid().isBlank()) {
                conversationRepository.update(new LambdaUpdateWrapper<AgentConversationEntity>()
                        .eq(AgentConversationEntity::getConversationUid, messageEntity.getConversationUid())
                        .set(AgentConversationEntity::getLastTaskTerminalTime, now)
                        .set(AgentConversationEntity::getUpdatedTime, now));
            }
        }
    }

    @Override
    public void accumulateMessageTokenUsage(String messageUid,
                                            String provider,
                                            String modelName,
                                            Integer inputTokens,
                                            Integer cachedInputTokens,
                                            Integer outputTokens,
                                            Integer totalTokens) {
        int input = sanitizeTokenCount(inputTokens);
        int cachedInput = sanitizeTokenCount(cachedInputTokens);
        int output = sanitizeTokenCount(outputTokens);
        int total = sanitizeTokenCount(totalTokens);
        if (total == 0 && (input > 0 || output > 0)) {
            total = input + output;
        }
        if (input > 0 && cachedInput > input) {
            cachedInput = input;
        }

        if (input <= 0 && cachedInput <= 0 && output <= 0 && total <= 0) {
            return;
        }

        LambdaUpdateWrapper<AgentMessageEntity> update = new LambdaUpdateWrapper<AgentMessageEntity>()
                .eq(AgentMessageEntity::getMessageUid, messageUid)
                .set(AgentMessageEntity::getUpdatedTime, LocalDateTime.now());
        if (input > 0) {
            update.setSql("input_tokens = input_tokens + " + input);
        }
        if (cachedInput > 0) {
            update.setSql("cached_input_tokens = cached_input_tokens + " + cachedInput);
        }
        if (output > 0) {
            update.setSql("output_tokens = output_tokens + " + output);
        }
        if (total > 0) {
            update.setSql("total_tokens = total_tokens + " + total);
        }
        messageRepository.update(update);

        AgentMessageEntity messageEntity = messageRepository.findByMessageId(messageUid);
        if (messageEntity == null || messageEntity.getConversationUid() == null || messageEntity.getConversationUid().isBlank()) {
            return;
        }
        LambdaUpdateWrapper<AgentConversationEntity> conversationUpdate = new LambdaUpdateWrapper<AgentConversationEntity>()
                .eq(AgentConversationEntity::getConversationUid, messageEntity.getConversationUid())
                .set(AgentConversationEntity::getUpdatedTime, LocalDateTime.now());
        if (input > 0) {
            conversationUpdate.setSql("input_tokens = input_tokens + " + input);
        }
        if (cachedInput > 0) {
            conversationUpdate.setSql("cached_input_tokens = cached_input_tokens + " + cachedInput);
        }
        if (output > 0) {
            conversationUpdate.setSql("output_tokens = output_tokens + " + output);
        }
        if (total > 0) {
            conversationUpdate.setSql("total_tokens = total_tokens + " + total);
        }
        conversationRepository.update(conversationUpdate);
    }

    @Override
    public void saveSteps(String conversationUid, String messageUid, List<PlanStep> steps) {
        LocalDateTime now = LocalDateTime.now();
        for (PlanStep step : steps) {
            AgentStepEntity entity = new AgentStepEntity();
            entity.setStepUid(step.stepUid());
            entity.setMessageUid(messageUid);
            entity.setConversationUid(conversationUid);
            entity.setRoundIndex(step.roundIndex());
            entity.setStepIndex(step.stepIndex());
            entity.setTitle(truncate(step.title(), MAX_STEP_TITLE_LENGTH));
            entity.setToolName(step.toolName());
            entity.setToolArgs(JsonUtil.toJson(step.toolArgs()));
            entity.setDoneCriteria(truncate(step.doneCriteria(), MAX_STEP_DONE_CRITERIA_LENGTH));
            entity.setRiskLevel(step.riskLevel().name());
            entity.setStatus(step.status().name());
            entity.setRetryCount(step.retryCount());
            entity.setApprovalStatus(step.approvalStatus().name());
            entity.setLastError(step.lastError());
            entity.setOutputText(step.outputText());
            entity.setCreatedTime(now);
            entity.setUpdatedTime(now);
            stepRepository.save(entity);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (maxLength <= 0 || value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    @Override
    public List<PlanStep> listSteps(String messageUid) {
        return stepRepository.listByMessageId(messageUid)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PlanStep> listSteps(String messageUid, int roundIndex) {
        return stepRepository.listByMessageIdAndRoundIndex(messageUid, roundIndex)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<PlanStep> findStep(String stepUid) {
        AgentStepEntity entity = stepRepository.findByStepId(stepUid);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<String> findMessageIdByStep(String stepUid) {
        AgentStepEntity entity = stepRepository.findByStepId(stepUid);
        return entity == null || entity.getMessageUid() == null || entity.getMessageUid().isBlank()
                ? Optional.empty()
                : Optional.of(entity.getMessageUid());
    }

    @Override
    public void updateStepStatus(String stepUid, StepStatus status, int retryCount, String errorMessage, String outputText) {
        LambdaUpdateWrapper<AgentStepEntity> update = new LambdaUpdateWrapper<AgentStepEntity>()
                .eq(AgentStepEntity::getStepUid, stepUid)
                .set(AgentStepEntity::getStatus, status.name())
                .set(AgentStepEntity::getRetryCount, retryCount)
                .set(AgentStepEntity::getLastError, errorMessage)
                .set(AgentStepEntity::getUpdatedTime, LocalDateTime.now());
        if (outputText != null) {
            update.set(AgentStepEntity::getOutputText, outputText);
        }
        stepRepository.update(update);
    }

    @Override
    public void updateStepApproval(String stepUid, ApprovalStatus approvalStatus, StepStatus stepStatus) {
        stepRepository.update(new LambdaUpdateWrapper<AgentStepEntity>()
                .eq(AgentStepEntity::getStepUid, stepUid)
                .set(AgentStepEntity::getApprovalStatus, approvalStatus.name())
                .set(AgentStepEntity::getStatus, stepStatus.name())
                .set(AgentStepEntity::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    public void appendEvent(AgentEvent event) {
        AgentEventEntity entity = new AgentEventEntity();
        entity.setEventUid(event.id());
        entity.setConversationUid(event.conversationUid());
        entity.setMessageUid(event.messageUid() == null ? "" : event.messageUid());
        entity.setStepUid(event.stepUid() == null ? "" : event.stepUid());
        entity.setEventType(event.eventType().name());
        entity.setPayload(JsonUtil.toJson(event.payload()));
        entity.setCreatedTime(toLocalDateTime(event.timestamp()));
        eventRepository.save(entity);
    }

    @Override
    public List<AgentEvent> listEventsByMessage(String messageUid) {
        return eventRepository.listByMessageUid(messageUid).stream()
                .map(this::toDomain)
                .toList();
    }

    private AgentConversation toDomain(AgentConversationEntity entity) {
        return new AgentConversation(
                entity.getConversationUid(),
                entity.getAgentGroupUid(),
                entity.getAgentUid(),
                entity.getChannel() == null || entity.getChannel().isBlank() ? "web" : entity.getChannel(),
                entity.getTitle(),
                Boolean.TRUE.equals(entity.getPinned()),
                entity.getInputTokens() == null ? 0 : entity.getInputTokens(),
                entity.getCachedInputTokens() == null ? 0 : entity.getCachedInputTokens(),
                entity.getOutputTokens() == null ? 0 : entity.getOutputTokens(),
                entity.getTotalTokens() == null ? 0 : entity.getTotalTokens(),
                toNullableInstant(entity.getLastTaskTerminalTime()),
                toNullableInstant(entity.getLastReadAt()),
                toInstant(entity.getLastUserMessageTime()),
                toInstant(entity.getCreatedTime()),
                toInstant(entity.getUpdatedTime())
        );
    }

    private AgentMessage toDomain(AgentMessageEntity entity) {
        return new AgentMessage(
                entity.getMessageUid(),
                entity.getConversationUid(),
                entity.getParentMessageUid(),
                entity.getRole(),
                entity.getContent(),
                MessageStatus.valueOf(entity.getStatus()),
                entity.getProvider(),
                entity.getModelName(),
                entity.getInputTokens() == null ? 0 : entity.getInputTokens(),
                entity.getCachedInputTokens() == null ? 0 : entity.getCachedInputTokens(),
                entity.getOutputTokens() == null ? 0 : entity.getOutputTokens(),
                entity.getTotalTokens() == null ? 0 : entity.getTotalTokens(),
                toInstant(entity.getCreatedTime()),
                toInstant(entity.getUpdatedTime())
        );
    }

    private AgentEvent toDomain(AgentEventEntity entity) {
        JsonNode payload = JsonUtil.fromJsonQuietly(entity.getPayload(), JsonNode.class)
                .orElse(JsonUtil.mapper().createObjectNode());
        return new AgentEvent(
                entity.getEventUid(),
                AgentEventType.valueOf(entity.getEventType()),
                entity.getConversationUid(),
                entity.getMessageUid(),
                entity.getStepUid(),
                toInstant(entity.getCreatedTime()),
                payload
        );
    }

    private PlanStep toDomain(AgentStepEntity entity) {
        JsonNode argsNode = entity.getToolArgs() == null || entity.getToolArgs().isBlank()
                ? JsonUtil.mapper().createObjectNode()
                : JsonUtil.fromJson(entity.getToolArgs(), JsonNode.class);
        return new PlanStep(
                entity.getStepUid(),
                entity.getRoundIndex() == null ? 1 : entity.getRoundIndex(),
                entity.getStepIndex() == null ? 1 : entity.getStepIndex(),
                entity.getTitle(),
                entity.getToolName(),
                argsNode,
                RiskLevel.valueOf(entity.getRiskLevel()),
                entity.getDoneCriteria(),
                StepStatus.valueOf(entity.getStatus()),
                entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
                entity.getLastError(),
                entity.getOutputText(),
                ApprovalStatus.valueOf(entity.getApprovalStatus())
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, SYSTEM_ZONE);
    }

    private int sanitizeTokenCount(Integer value) {
        if (value == null || value <= 0) {
            return 0;
        }
        return value;
    }

    private Instant toInstant(LocalDateTime time) {
        return time == null ? Instant.now() : Timestamp.valueOf(time).toInstant();
    }

    private Instant toNullableInstant(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time).toInstant();
    }
}
