package ai.nomoclaw.bot.store;

import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.AgentEvent;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.StepStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAgentStore implements AgentStore {

    private final Map<String, AgentConversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, AgentMessage> messages = new ConcurrentHashMap<>();
    private final Map<String, List<PlanStep>> stepsByMessage = new ConcurrentHashMap<>();
    private final Map<String, PlanStep> stepsById = new ConcurrentHashMap<>();
    private final List<AgentEvent> events = new ArrayList<>();

    @Override
    public AgentConversation createConversation(String conversationUid, String agentGroupUid, String agentUid) {
        return createConversation(conversationUid, agentGroupUid, agentUid, "web");
    }

    @Override
    public AgentConversation createConversation(String conversationUid, String agentGroupUid, String agentUid, String channel) {
        Instant now = Instant.now();
        AgentConversation conversation = new AgentConversation(
                conversationUid,
                agentGroupUid,
                agentUid,
                channel == null || channel.isBlank() ? "web" : channel,
                "",
                false,
                0,
                0,
                0,
                now,
                now
        );
        conversations.put(conversationUid, conversation);
        return conversation;
    }

    @Override
    public List<AgentConversation> listConversations() {
        return conversations.values().stream()
                .sorted(
                        Comparator.comparing(AgentConversation::pinned).reversed()
                                .thenComparing(AgentConversation::updatedAt, Comparator.reverseOrder())
                                .thenComparing(AgentConversation::createdAt, Comparator.reverseOrder())
                )
                .toList();
    }

    @Override
    public Optional<AgentConversation> findConversation(String conversationUid) {
        return Optional.ofNullable(conversations.get(conversationUid));
    }

    @Override
    public void deleteConversation(String conversationUid) {
        conversations.remove(conversationUid);
        List<String> messageUids = messages.values().stream()
                .filter(message -> conversationUid.equals(message.conversationUid()))
                .map(AgentMessage::messageUid)
                .toList();
        List<String> stepUids = stepsByMessage.entrySet().stream()
                .filter(entry -> messageUids.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .map(PlanStep::stepUid)
                .toList();
        messageUids.forEach(messageUid -> {
            messages.remove(messageUid);
            stepsByMessage.remove(messageUid);
        });
        stepUids.forEach(stepsById::remove);
        synchronized (this) {
            events.removeIf(event -> conversationUid.equals(event.conversationUid()));
        }
    }

    @Override
    public void touchConversation(String conversationUid) {
        conversations.computeIfPresent(conversationUid, (id, oldConversation) -> new AgentConversation(
                oldConversation.conversationUid(),
                oldConversation.agentGroupUid(),
                oldConversation.agentUid(),
                oldConversation.channel(),
                oldConversation.title(),
                oldConversation.pinned(),
                oldConversation.inputTokens(),
                oldConversation.outputTokens(),
                oldConversation.totalTokens(),
                oldConversation.createdAt(),
                Instant.now()
        ));
    }

    @Override
    public void updateConversationTitle(String conversationUid, String title) {
        conversations.computeIfPresent(conversationUid, (id, oldConversation) -> new AgentConversation(
                oldConversation.conversationUid(),
                oldConversation.agentGroupUid(),
                oldConversation.agentUid(),
                oldConversation.channel(),
                title,
                oldConversation.pinned(),
                oldConversation.inputTokens(),
                oldConversation.outputTokens(),
                oldConversation.totalTokens(),
                oldConversation.createdAt(),
                Instant.now()
        ));
    }

    @Override
    public void updateConversationPinned(String conversationUid, boolean pinned) {
        conversations.computeIfPresent(conversationUid, (id, oldConversation) -> new AgentConversation(
                oldConversation.conversationUid(),
                oldConversation.agentGroupUid(),
                oldConversation.agentUid(),
                oldConversation.channel(),
                oldConversation.title(),
                pinned,
                oldConversation.inputTokens(),
                oldConversation.outputTokens(),
                oldConversation.totalTokens(),
                oldConversation.createdAt(),
                oldConversation.updatedAt()
        ));
    }

    @Override
    public AgentMessage createUserMessage(String messageUid,
                                          String conversationUid,
                                          String content,
                                          int maxRounds,
                                          String provider,
                                          String modelName) {
        Instant now = Instant.now();
        AgentMessage message = new AgentMessage(messageUid, conversationUid, null, "user", content,
                MessageStatus.CREATED,
                provider == null ? "" : provider.trim(),
                modelName == null ? "" : modelName.trim(),
                0, 0, 0, now, now);
        messages.put(messageUid, message);
        touchConversation(conversationUid);
        return message;
    }

    @Override
    public AgentMessage createAssistantMessage(String messageUid, String conversationUid, String parentMessageUid, String content) {
        Instant now = Instant.now();
        AgentMessage message = new AgentMessage(messageUid, conversationUid, parentMessageUid, "assistant", content,
                MessageStatus.COMPLETED, "", "", 0, 0, 0, now, now);
        messages.put(messageUid, message);
        touchConversation(conversationUid);
        return message;
    }

    @Override
    public Optional<AgentMessage> findMessage(String messageUid) {
        return Optional.ofNullable(messages.get(messageUid));
    }

    @Override
    public List<AgentMessage> listMessagesByConversation(String conversationUid) {
        return messages.values().stream()
                .filter(message -> message.conversationUid().equals(conversationUid))
                .sorted(Comparator.comparing(AgentMessage::createdAt))
                .toList();
    }

    @Override
    public Optional<AgentMessage> findLatestUserMessageByConversation(String conversationUid) {
        return messages.values().stream()
                .filter(message -> message.conversationUid().equals(conversationUid) && "user".equals(message.role()))
                .max(Comparator.comparing(AgentMessage::createdAt));
    }

    @Override
    public void updateMessageStatus(String messageUid, MessageStatus status) {
        messages.computeIfPresent(messageUid, (id, oldMessage) -> new AgentMessage(
                oldMessage.messageUid(),
                oldMessage.conversationUid(),
                oldMessage.parentMessageUid(),
                oldMessage.role(),
                oldMessage.content(),
                status,
                oldMessage.provider(),
                oldMessage.modelName(),
                oldMessage.inputTokens(),
                oldMessage.outputTokens(),
                oldMessage.totalTokens(),
                oldMessage.createdAt(),
                Instant.now()
        ));
    }

    @Override
    public void accumulateMessageTokenUsage(String messageUid,
                                            String provider,
                                            String modelName,
                                            Integer inputTokens,
                                            Integer outputTokens,
                                            Integer totalTokens) {
        int normalizedInput = sanitizeTokenCount(inputTokens);
        int normalizedOutput = sanitizeTokenCount(outputTokens);
        int normalizedTotal = sanitizeTokenCount(totalTokens);
        if (normalizedTotal == 0 && (normalizedInput > 0 || normalizedOutput > 0)) {
            normalizedTotal = normalizedInput + normalizedOutput;
        }

        final int input = normalizedInput;
        final int output = normalizedOutput;
        final int total = normalizedTotal;
        messages.computeIfPresent(messageUid, (id, oldMessage) -> new AgentMessage(
                oldMessage.messageUid(),
                oldMessage.conversationUid(),
                oldMessage.parentMessageUid(),
                oldMessage.role(),
                oldMessage.content(),
                oldMessage.status(),
                oldMessage.provider(),
                oldMessage.modelName(),
                oldMessage.inputTokens() + input,
                oldMessage.outputTokens() + output,
                oldMessage.totalTokens() + total,
                oldMessage.createdAt(),
                Instant.now()
        ));

        AgentMessage updatedMessage = messages.get(messageUid);
        if (updatedMessage == null) {
            return;
        }
        conversations.computeIfPresent(updatedMessage.conversationUid(), (conversationUid, oldConversation) -> new AgentConversation(
                oldConversation.conversationUid(),
                oldConversation.agentGroupUid(),
                oldConversation.agentUid(),
                oldConversation.channel(),
                oldConversation.title(),
                oldConversation.pinned(),
                oldConversation.inputTokens() + input,
                oldConversation.outputTokens() + output,
                oldConversation.totalTokens() + total,
                oldConversation.createdAt(),
                Instant.now()
        ));
    }

    @Override
    public void saveSteps(String conversationUid, String messageUid, List<PlanStep> steps) {
        List<PlanStep> existing = new ArrayList<>(stepsByMessage.getOrDefault(messageUid, List.of()));
        existing.addAll(steps);
        stepsByMessage.put(messageUid, existing);
        for (PlanStep step : steps) {
            stepsById.put(step.stepUid(), step);
        }
    }

    @Override
    public List<PlanStep> listSteps(String messageUid) {
        return new ArrayList<>(stepsByMessage.getOrDefault(messageUid, List.of()));
    }

    @Override
    public List<PlanStep> listSteps(String messageUid, int roundIndex) {
        return stepsByMessage.getOrDefault(messageUid, List.of()).stream()
                .filter(step -> step.roundIndex() == roundIndex)
                .sorted(Comparator.comparingInt(PlanStep::stepIndex))
                .toList();
    }

    @Override
    public Optional<PlanStep> findStep(String stepUid) {
        return Optional.ofNullable(stepsById.get(stepUid));
    }

    @Override
    public void updateStepStatus(String stepUid, StepStatus status, int retryCount, String errorMessage) {
        updateStep(stepUid, oldStep -> new PlanStep(
                oldStep.stepUid(),
                oldStep.roundIndex(),
                oldStep.stepIndex(),
                oldStep.title(),
                oldStep.toolName(),
                oldStep.toolArgs(),
                oldStep.riskLevel(),
                oldStep.doneCriteria(),
                status,
                retryCount,
                errorMessage,
                oldStep.approvalStatus()
        ));
    }

    @Override
    public Optional<String> findMessageIdByStep(String stepUid) {
        String messageUid = findMessageIdByStepInternal(stepUid);
        return messageUid.isBlank() ? Optional.empty() : Optional.of(messageUid);
    }

    @Override
    public void updateStepApproval(String stepUid, ApprovalStatus approvalStatus, StepStatus stepStatus) {
        updateStep(stepUid, oldStep -> new PlanStep(
                oldStep.stepUid(),
                oldStep.roundIndex(),
                oldStep.stepIndex(),
                oldStep.title(),
                oldStep.toolName(),
                oldStep.toolArgs(),
                oldStep.riskLevel(),
                oldStep.doneCriteria(),
                stepStatus,
                oldStep.retryCount(),
                oldStep.lastError(),
                approvalStatus
        ));
    }

    @Override
    public synchronized void appendEvent(AgentEvent event) {
        events.add(event);
    }

    @Override
    public synchronized List<AgentEvent> listEventsByMessage(String messageUid) {
        return events.stream()
                .filter(event -> messageUid.equals(event.messageUid()))
                .sorted(Comparator.comparing(AgentEvent::timestamp))
                .toList();
    }

    private void updateStep(String stepUid, java.util.function.Function<PlanStep, PlanStep> updater) {
        PlanStep old = stepsById.get(stepUid);
        if (old == null) {
            return;
        }
        PlanStep next = updater.apply(old);
        stepsById.put(stepUid, next);
        List<PlanStep> messageSteps = stepsByMessage.getOrDefault(findMessageIdByStepInternal(stepUid), List.of());
        for (int i = 0; i < messageSteps.size(); i++) {
            if (messageSteps.get(i).stepUid().equals(stepUid)) {
                messageSteps.set(i, next);
                break;
            }
        }
    }

    private String findMessageIdByStepInternal(String stepUid) {
        for (Map.Entry<String, List<PlanStep>> entry : stepsByMessage.entrySet()) {
            boolean found = entry.getValue().stream().anyMatch(step -> step.stepUid().equals(stepUid));
            if (found) {
                return entry.getKey();
            }
        }
        return "";
    }

    private int sanitizeTokenCount(Integer value) {
        if (value == null || value <= 0) {
            return 0;
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
