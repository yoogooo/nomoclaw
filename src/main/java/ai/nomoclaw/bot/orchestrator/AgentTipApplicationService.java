package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.command.CreateAgentTipCommand;
import ai.nomoclaw.bot.application.command.UpdateAgentTipCommand;
import ai.nomoclaw.bot.application.dto.AgentTipDto;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.prompt.PromptLoader;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentGroupDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentGroupMemberEntity;
import ai.nomoclaw.bot.store.entity.AgentTipEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupMemberRepository;
import ai.nomoclaw.bot.store.repository.AgentTipRepository;
import ai.nomoclaw.bot.workspace.AgentWorkspaceConfig;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AgentTipApplicationService {

    private static final String DEFAULT_AGENT_UID = "agent_general_assistant";

    private final AgentStore store;
    private final TipSummaryChatService tipSummaryChatService;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentGroupDefinitionRepository agentGroupDefinitionRepository;
    private final AgentGroupMemberRepository agentGroupMemberRepository;
    private final AgentTipRepository agentTipRepository;

    public AgentTipApplicationService(AgentStore store,
                                      TipSummaryChatService tipSummaryChatService,
                                      AgentDefinitionRepository agentDefinitionRepository,
                                      AgentGroupDefinitionRepository agentGroupDefinitionRepository,
                                      AgentGroupMemberRepository agentGroupMemberRepository,
                                      AgentTipRepository agentTipRepository) {
        this.store = store;
        this.tipSummaryChatService = tipSummaryChatService;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentGroupDefinitionRepository = agentGroupDefinitionRepository;
        this.agentGroupMemberRepository = agentGroupMemberRepository;
        this.agentTipRepository = agentTipRepository;
    }

    public List<AgentTipDto> listAgentTips(String agentUid) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        return agentTipRepository.listActiveByAgentUid(normalizedAgentUid).stream()
                .map(this::toAgentTipResponse)
                .toList();
    }

    public AgentTipDto createAgentTip(String agentUid, CreateAgentTipCommand request) {
        if (request == null) {
            request = new CreateAgentTipCommand(null, null, null, null, null, null, false);
        }
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        if (agentDefinitionRepository.findByUid(normalizedAgentUid) == null) {
            throw new IllegalArgumentException("agent not found: " + normalizedAgentUid);
        }

        String sourceConversationUid = trim(request.sourceConversationUid());
        String sourceMessageUid = trim(request.sourceMessageUid());
        String sourceContent = request.sourceContent() == null ? "" : request.sourceContent().trim();
        String title = request.title() == null ? "" : request.title().trim();
        String summary = request.summary() == null ? "" : request.summary().trim();
        boolean shouldGenerateBestPractice = Boolean.TRUE.equals(request.generateBestPractice())
                || (!sourceConversationUid.isBlank() && !sourceMessageUid.isBlank());
        if (shouldGenerateBestPractice) {
            BestPracticeTip generated = generateBestPracticeTip(sourceConversationUid, sourceMessageUid);
            title = generated.title();
            summary = generated.summary();
            sourceContent = generated.sourceContent();
        }
        if (sourceContent.isBlank() && summary.isBlank() && title.isBlank()) {
            throw new IllegalArgumentException("tip content must not be blank");
        }
        if (title.isBlank()) {
            title = buildTipTitle(sourceContent.isBlank() ? summary : sourceContent);
        }
        if (summary.isBlank()) {
            summary = buildTipSummary(sourceContent.isBlank() ? title : sourceContent);
        }
        if (sourceContent.isBlank()) {
            sourceContent = summary;
        }
        summary = truncateByChars(summary, 300);

        AgentTipEntity existing = sourceMessageUid.isBlank()
                ? null
                : agentTipRepository.findByAgentUidAndSourceMessageUid(normalizedAgentUid, sourceMessageUid);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setTitle(title);
            existing.setSummary(summary);
            existing.setSourceContent(sourceContent);
            existing.setSourceConversationUid(emptyToNull(sourceConversationUid));
            existing.setSourceMessageUid(emptyToNull(sourceMessageUid));
            existing.setSourceTime(parseNullableLocalDateTime(request.sourceTime()));
            existing.setStatus("ACTIVE");
            existing.setUpdatedTime(now);
            agentTipRepository.updateById(existing);
            return toAgentTipResponse(existing);
        }

        AgentTipEntity tip = new AgentTipEntity();
        tip.setTipUid(UUID.randomUUID().toString());
        tip.setAgentUid(normalizedAgentUid);
        tip.setTitle(title);
        tip.setSummary(summary);
        tip.setSourceContent(sourceContent);
        tip.setSourceConversationUid(emptyToNull(sourceConversationUid));
        tip.setSourceMessageUid(emptyToNull(sourceMessageUid));
        tip.setSourceTime(parseNullableLocalDateTime(request.sourceTime()));
        tip.setStatus("ACTIVE");
        tip.setSortIndex(0);
        tip.setExtConfig("{}");
        tip.setCreatedTime(now);
        tip.setUpdatedTime(now);
        agentTipRepository.save(tip);
        return toAgentTipResponse(tip);
    }

    public void deleteAgentTip(String agentUid, String tipUid) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        String normalizedTipUid = tipUid == null ? "" : tipUid.trim();
        if (normalizedTipUid.isBlank()) {
            throw new IllegalArgumentException("tipUid must not be blank");
        }
        AgentTipEntity tip = agentTipRepository.findByAgentUidAndTipUid(normalizedAgentUid, normalizedTipUid);
        if (tip == null) {
            throw new IllegalArgumentException("tip not found: " + normalizedTipUid);
        }
        tip.setStatus("DISABLED");
        tip.setUpdatedTime(LocalDateTime.now());
        agentTipRepository.updateById(tip);
    }

    public void purgeAgentTips(String agentUid) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        agentTipRepository.deleteByAgentUid(normalizedAgentUid);
    }

    public AgentTipDto updateAgentTip(String agentUid, String tipUid, UpdateAgentTipCommand request) {
        String normalizedAgentUid = normalizeAgentUid(agentUid);
        String normalizedTipUid = tipUid == null ? "" : tipUid.trim();
        if (normalizedTipUid.isBlank()) {
            throw new IllegalArgumentException("tipUid must not be blank");
        }
        AgentTipEntity tip = agentTipRepository.findByAgentUidAndTipUid(normalizedAgentUid, normalizedTipUid);
        if (tip == null || !"ACTIVE".equals(tip.getStatus())) {
            throw new IllegalArgumentException("tip not found: " + normalizedTipUid);
        }

        String title = request == null || request.title() == null ? "" : request.title().trim();
        String summary = request == null || request.summary() == null ? "" : request.summary().trim();
        String sourceContent = request == null || request.sourceContent() == null ? "" : request.sourceContent().trim();
        if (title.isBlank() && summary.isBlank() && sourceContent.isBlank()) {
            throw new IllegalArgumentException("tip content must not be blank");
        }
        if (title.isBlank()) {
            title = buildTipTitle(sourceContent.isBlank() ? summary : sourceContent);
        }
        if (summary.isBlank()) {
            summary = buildTipSummary(sourceContent.isBlank() ? title : sourceContent);
        }
        if (sourceContent.isBlank()) {
            sourceContent = summary;
        }

        tip.setTitle(title);
        tip.setSummary(truncateByChars(summary, 300));
        tip.setSourceContent(truncateByChars(sourceContent, 2000));
        tip.setUpdatedTime(LocalDateTime.now());
        agentTipRepository.updateById(tip);
        return toAgentTipResponse(tip);
    }

    private BestPracticeTip generateBestPracticeTip(String sourceConversationUid, String sourceMessageUid) {
        if (sourceConversationUid.isBlank() || sourceMessageUid.isBlank()) {
            throw new IllegalArgumentException("sourceConversationUid and sourceMessageUid are required");
        }
        AgentConversation conversation = store.findConversation(sourceConversationUid)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found: " + sourceConversationUid));
        List<AgentMessage> allMessages = store.listMessagesByConversation(sourceConversationUid);
        int anchorIndex = -1;
        for (int i = 0; i < allMessages.size(); i++) {
            if (sourceMessageUid.equals(allMessages.get(i).messageUid())) {
                anchorIndex = i;
                break;
            }
        }
        if (anchorIndex < 0) {
            throw new IllegalArgumentException("source message not found in conversation: " + sourceMessageUid);
        }
        AgentMessage finalMessage = allMessages.get(anchorIndex);
        if (!"assistant".equals(finalMessage.role())) {
            throw new IllegalArgumentException("source message must be assistant role");
        }

        int fromIndex = Math.max(0, anchorIndex - 5);
        List<AgentMessage> recentMessages = allMessages.subList(fromIndex, anchorIndex);
        List<PlanStep> steps = store.listSteps(sourceMessageUid).stream()
                .sorted(Comparator.comparingInt(PlanStep::roundIndex).thenComparingInt(PlanStep::stepIndex))
                .toList();
        AgentDefinitionEntity executionAgent = resolveExecutionAgent(conversation);
        TipSummaryChatService.TipEvaluationResult evaluation = tipSummaryChatService.evaluate(
                buildPromptContext(conversation, executionAgent, sourceConversationUid, sourceMessageUid),
                recentMessages.stream()
                        .map(item -> new TipSummaryChatService.RecentMessage(item.role(), item.content()))
                        .toList(),
                steps.stream()
                        .map(step -> new TipSummaryChatService.StepDigest(
                                step.roundIndex(),
                                step.stepIndex(),
                                step.title(),
                                step.toolName(),
                                step.status() == null ? "" : step.status().name(),
                                step.retryCount(),
                                step.doneCriteria(),
                                step.lastError()
                        ))
                        .toList(),
                finalMessage.content()
        );
        if (!evaluation.shouldSave()) {
            String reason = normalizeText(evaluation.reason());
            if (reason.isBlank()) {
                reason = "本次记录缺少足够的可复用价值，暂不保存为锦囊";
            }
            throw new IllegalArgumentException(reason);
        }
        String sourceContent = normalizeText(evaluation.content());
        if (sourceContent.isBlank()) {
            sourceContent = buildFallbackBestPractice(recentMessages, finalMessage, steps).sourceContent();
        }
        String title = normalizeText(evaluation.title());
        if (title.isBlank()) {
            title = buildTipTitle(sourceContent);
        }
        String summary = normalizeText(evaluation.summary());
        if (summary.isBlank()) {
            summary = buildTipSummary(sourceContent);
        }
        return new BestPracticeTip(title, truncateByChars(summary, 300), truncateByChars(sourceContent, 1000));
    }

    private BestPracticeTip buildFallbackBestPractice(List<AgentMessage> recentMessages,
                                                      AgentMessage finalMessage,
                                                      List<PlanStep> steps) {
        String goal = recentMessages.stream()
                .filter(item -> "user".equals(item.role()) && hasMeaningfulText(item.content()))
                .reduce((first, second) -> second)
                .map(item -> normalizeText(item.content()))
                .orElse("完成当前用户任务并产出可用结果");
        String stepPath = steps.stream()
                .limit(4)
                .map(step -> normalizeText(step.title()).isBlank()
                        ? normalizeText(step.toolName())
                        : normalizeText(step.title()))
                .filter(text -> !text.isBlank())
                .toList()
                .stream()
                .reduce((left, right) -> left + " -> " + right)
                .orElse("确认目标 -> 执行关键操作 -> 校验结果");
        String pitfalls = steps.stream()
                .filter(step -> step.retryCount() > 0 || hasMeaningfulText(step.lastError()) || step.status() == StepStatus.FAILED)
                .map(step -> hasMeaningfulText(step.lastError()) ? normalizeText(step.lastError()) : "步骤重试后才成功")
                .findFirst()
                .orElse("避免跳过前置校验，出现异常时先定位失败步骤再重试");
        String completion = normalizeText(finalMessage.content());
        if (completion.isBlank()) {
            completion = "结果可直接交付且包含必要信息";
        } else {
            completion = abbreviate(completion, 80);
        }
        String summary = "目标：" + abbreviate(goal, 48)
                + "；路径：" + abbreviate(stepPath, 88)
                + "；避坑：" + abbreviate(pitfalls, 72)
                + "；完成判定：" + completion;
        summary = truncateByChars(normalizeText(summary), 300);
        return new BestPracticeTip(buildTipTitle(summary), summary, summary);
    }

    private PromptLoader.PromptContext buildPromptContext(AgentConversation conversation,
                                                          AgentDefinitionEntity executionAgent,
                                                          String sessionId,
                                                          String messageUid) {
        AgentGroupDefinitionEntity group = resolveConversationGroup(conversation);
        String agentName = executionAgent == null ? NomoClawPaths.DEFAULT_AGENT_NAME : executionAgent.getAgentName();
        AgentWorkspaceConfig workspaceConfig = executionAgent == null
                ? AgentWorkspaceConfig.defaults(agentName)
                : AgentWorkspaceConfig.resolve(agentName, executionAgent.getWorkspace());
        return PromptLoader.PromptContext.forAgent(
                sessionId,
                messageUid,
                conversation.channel() == null || conversation.channel().isBlank() ? "web" : conversation.channel(),
                group == null ? "" : group.getGroupName(),
                agentName,
                NomoClawPaths.root(),
                NomoClawPaths.agentHome(agentName),
                workspaceConfig.workspaceDir(),
                workspaceConfig.tmpDir(),
                workspaceConfig.reportDir()
        );
    }

    private AgentDefinitionEntity resolveExecutionAgent(AgentConversation conversation) {
        if (conversation.agentUid() != null && !conversation.agentUid().isBlank()) {
            return agentDefinitionRepository.findActiveByUid(conversation.agentUid());
        }
        if (conversation.agentGroupUid() == null || conversation.agentGroupUid().isBlank()) {
            return agentDefinitionRepository.findActiveByUid(DEFAULT_AGENT_UID);
        }
        AgentGroupDefinitionEntity group = agentGroupDefinitionRepository.findActiveByUid(conversation.agentGroupUid());
        if (group != null && group.getOwnerAgentUid() != null && !group.getOwnerAgentUid().isBlank()) {
            AgentDefinitionEntity owner = agentDefinitionRepository.findActiveByUid(group.getOwnerAgentUid());
            if (owner != null) {
                return owner;
            }
        }
        AgentGroupMemberEntity primaryMember = agentGroupMemberRepository.findPrimaryByGroupUid(conversation.agentGroupUid());
        if (primaryMember != null && primaryMember.getAgentUid() != null && !primaryMember.getAgentUid().isBlank()) {
            AgentDefinitionEntity primaryAgent = agentDefinitionRepository.findActiveByUid(primaryMember.getAgentUid());
            if (primaryAgent != null) {
                return primaryAgent;
            }
        }
        return agentDefinitionRepository.findActiveByUid(DEFAULT_AGENT_UID);
    }

    private AgentGroupDefinitionEntity resolveConversationGroup(AgentConversation conversation) {
        if (conversation.agentGroupUid() == null || conversation.agentGroupUid().isBlank()) {
            return null;
        }
        return agentGroupDefinitionRepository.findActiveByUid(conversation.agentGroupUid());
    }

    private AgentTipDto toAgentTipResponse(AgentTipEntity tip) {
        return new AgentTipDto(
                tip.getTipUid(),
                tip.getAgentUid(),
                tip.getTitle(),
                tip.getSummary(),
                tip.getSourceContent(),
                tip.getSourceConversationUid(),
                tip.getSourceMessageUid(),
                tip.getSourceTime(),
                tip.getCreatedTime(),
                tip.getUpdatedTime()
        );
    }

    private LocalDateTime parseNullableLocalDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ignore) {
            try {
                return Instant.parse(raw).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private String buildTipTitle(String content) {
        String normalized = normalizeText(content);
        if (normalized.isBlank()) {
            return "锦囊";
        }
        String firstLine = normalized.split("[。！？\\n]")[0].trim();
        if (firstLine.isBlank()) {
            firstLine = normalized;
        }
        return abbreviate(firstLine, 18);
    }

    private String buildTipSummary(String content) {
        String normalized = normalizeText(content);
        if (normalized.isBlank()) {
            return "围绕当前任务沉淀了可复用执行步骤与注意事项。";
        }
        return abbreviate(normalized, 80);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String trim(String text) {
        return text == null ? "" : text.trim();
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String truncateByChars(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (maxLength <= 0) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private boolean hasMeaningfulText(String text) {
        return text != null && !text.isBlank();
    }

    private String normalizeAgentUid(String agentUid) {
        return agentUid == null || agentUid.isBlank() ? DEFAULT_AGENT_UID : agentUid.trim();
    }

    private record BestPracticeTip(String title, String summary, String sourceContent) {
    }
}
