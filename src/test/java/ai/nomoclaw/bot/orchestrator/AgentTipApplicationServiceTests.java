package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.application.command.CreateAgentTipCommand;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.RiskLevel;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.entity.AgentTipEntity;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupDefinitionRepository;
import ai.nomoclaw.bot.store.repository.AgentGroupMemberRepository;
import ai.nomoclaw.bot.store.repository.AgentTipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentTipApplicationServiceTests {

    private FakeAgentStore store;
    private FakeTipSummaryChatService tipSummaryChatService;
    private FakeAgentDefinitionRepository agentDefinitionRepository;
    private FakeAgentGroupDefinitionRepository agentGroupDefinitionRepository;
    private FakeAgentGroupMemberRepository agentGroupMemberRepository;
    private FakeAgentTipRepository agentTipRepository;
    private AgentTipApplicationService service;
    private Path originalRoot;

    @BeforeEach
    void setUp() throws Exception {
        originalRoot = ai.nomoclaw.bot.workspace.NomoClawPaths.root();
        ai.nomoclaw.bot.workspace.NomoClawPaths.configureRoot(Files.createTempDirectory("nomoclaw-tip-test"));
        store = new FakeAgentStore();
        tipSummaryChatService = new FakeTipSummaryChatService();
        agentDefinitionRepository = new FakeAgentDefinitionRepository();
        agentGroupDefinitionRepository = new FakeAgentGroupDefinitionRepository();
        agentGroupMemberRepository = new FakeAgentGroupMemberRepository();
        agentTipRepository = new FakeAgentTipRepository();
        service = new AgentTipApplicationService(
                store,
                tipSummaryChatService,
                agentDefinitionRepository,
                agentGroupDefinitionRepository,
                agentGroupMemberRepository,
                agentTipRepository
        );
    }

    @AfterEach
    void tearDown() {
        if (originalRoot != null) {
            ai.nomoclaw.bot.workspace.NomoClawPaths.configureRoot(originalRoot);
        }
    }

    @Test
    void shouldRejectSavingBestPracticeWhenEvaluationRejects() {
        mockAgentLookup();
        mockConversationData();
        tipSummaryChatService.nextResult = TipSummaryChatService.TipEvaluationResult.reject("本次记录太普通，暂不保存为锦囊");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.createAgentTip("agent-1", new CreateAgentTipCommand(
                        null,
                        null,
                        null,
                        "conversation-1",
                        "message-2",
                        "2026-04-26T10:15:30",
                        true
                )));

        assertEquals("本次记录太普通，暂不保存为锦囊", ex.getMessage());
        assertEquals(null, agentTipRepository.savedTip);
        assertEquals(null, agentTipRepository.updatedTip);
    }

    @Test
    void shouldSaveBestPracticeWhenEvaluationApproves() {
        mockAgentLookup();
        mockConversationData();
        tipSummaryChatService.nextResult = TipSummaryChatService.TipEvaluationResult.save(
                "包含可复用执行路径与经验，适合沉淀为锦囊",
                "高质量排障锦囊",
                "先确认目标，再按步骤排查并恢复。",
                "目标：排障；路径：确认目标 -> 检查输入 -> 重试；避坑：记录错误并回看；完成判定：结果恢复并可交付。"
        );

        var result = service.createAgentTip("agent-1", new CreateAgentTipCommand(
                null,
                null,
                null,
                "conversation-1",
                "message-2",
                "2026-04-26T10:15:30",
                true
        ));

        assertNotNull(result);
        assertEquals("agent-1", result.agentUid());
        assertEquals("message-2", result.sourceMessageUid());
        assertEquals("高质量排障锦囊", result.title());
        assertEquals("先确认目标，再按步骤排查并恢复。", result.summary());
        assertEquals("目标：排障；路径：确认目标 -> 检查输入 -> 重试；避坑：记录错误并回看；完成判定：结果恢复并可交付。", result.sourceContent());
        assertEquals("message-1", store.lastListStepsMessageUid);
        assertEquals(1, tipSummaryChatService.lastSteps.size());
        assertEquals("检查输入", tipSummaryChatService.lastSteps.getFirst().title());

        assertNotNull(agentTipRepository.savedTip);
        assertEquals("agent-1", agentTipRepository.savedTip.getAgentUid());
        assertEquals("message-2", agentTipRepository.savedTip.getSourceMessageUid());
        assertEquals("高质量排障锦囊", agentTipRepository.savedTip.getTitle());
        assertEquals("先确认目标，再按步骤排查并恢复。", agentTipRepository.savedTip.getSummary());
        assertEquals("目标：排障；路径：确认目标 -> 检查输入 -> 重试；避坑：记录错误并回看；完成判定：结果恢复并可交付。", agentTipRepository.savedTip.getSourceContent());
    }

    private void mockAgentLookup() {
        AgentDefinitionEntity agent = new AgentDefinitionEntity();
        agent.setAgentUid("agent-1");
        agent.setAgentName("agent-one");
        agent.setDisplayName("Agent One");
        agent.setStatus("ACTIVE");
        agentDefinitionRepository.agent = agent;
    }

    private void mockConversationData() {
        store.conversation = new AgentConversation(
                "conversation-1",
                "group-1",
                "agent-1",
                "web",
                "conversation",
                false,
                0,
                0,
                0,
                0,
                Instant.parse("2026-04-26T01:00:00Z"),
                Instant.parse("2026-04-26T01:05:00Z")
        );
        store.messages = List.of(
                new AgentMessage(
                        "message-1",
                        "conversation-1",
                        null,
                        "user",
                        "请帮我排查这个问题",
                        MessageStatus.COMPLETED,
                        null,
                        null,
                        0,
                        0,
                        0,
                        0,
                        Instant.parse("2026-04-26T01:01:00Z"),
                        Instant.parse("2026-04-26T01:01:00Z")
                ),
                new AgentMessage(
                        "message-2",
                        "conversation-1",
                        "message-1",
                        "assistant",
                        "问题已经排查完成",
                        MessageStatus.COMPLETED,
                        null,
                        null,
                        0,
                        0,
                        0,
                        0,
                        Instant.parse("2026-04-26T01:02:00Z"),
                        Instant.parse("2026-04-26T01:02:00Z")
                )
        );
        store.steps = List.of(
                new PlanStep(
                        "step-1",
                        1,
                        1,
                        "检查输入",
                        "file_search",
                        null,
                        RiskLevel.LOW,
                        "确认输入内容",
                        StepStatus.COMPLETED,
                        1,
                        null,
                        "已恢复",
                        ApprovalStatus.APPROVED
                )
        );
    }

    private static final class FakeTipSummaryChatService extends TipSummaryChatService {
        private TipEvaluationResult nextResult = TipEvaluationResult.reject("not set");
        private List<StepDigest> lastSteps = List.of();

        private FakeTipSummaryChatService() {
            super(null);
        }

        @Override
        public TipEvaluationResult evaluate(ai.nomoclaw.bot.prompt.PromptLoader.PromptContext promptContext,
                                            List<RecentMessage> recentMessages,
                                            List<StepDigest> steps,
                                            String finalResult) {
            lastSteps = steps;
            return nextResult;
        }
    }

    private static final class FakeAgentStore implements AgentStore {
        private AgentConversation conversation;
        private List<AgentMessage> messages = List.of();
        private List<PlanStep> steps = List.of();
        private String lastListStepsMessageUid = "";

        @Override
        public AgentConversation createConversation(String conversationUid, String agentGroupUid, String agentUid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AgentConversation> listConversations() {
            return List.of();
        }

        @Override
        public Optional<AgentConversation> findConversation(String conversationUid) {
            return Optional.ofNullable(conversation);
        }

        @Override
        public void deleteConversation(String conversationUid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void touchConversation(String conversationUid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateConversationTitle(String conversationUid, String title) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateConversationPinned(String conversationUid, boolean pinned) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentMessage createUserMessage(String messageUid, String conversationUid, String content, int maxRounds, String provider, String modelName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentMessage createAssistantMessage(String messageUid, String conversationUid, String parentMessageUid, String content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AgentMessage> findMessage(String messageUid) {
            return Optional.empty();
        }

        @Override
        public List<AgentMessage> listMessagesByConversation(String conversationUid) {
            return messages;
        }

        @Override
        public Optional<AgentMessage> findLatestUserMessageByConversation(String conversationUid) {
            return Optional.empty();
        }

        @Override
        public void updateMessageStatus(String messageUid, MessageStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accumulateMessageTokenUsage(String messageUid,
                                                String provider,
                                                String modelName,
                                                Integer inputTokens,
                                                Integer cachedInputTokens,
                                                Integer outputTokens,
                                                Integer totalTokens) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveSteps(String conversationUid, String messageUid, List<PlanStep> steps) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PlanStep> listSteps(String messageUid) {
            lastListStepsMessageUid = messageUid;
            return steps;
        }

        @Override
        public List<PlanStep> listSteps(String messageUid, int roundIndex) {
            return steps;
        }

        @Override
        public Optional<PlanStep> findStep(String stepUid) {
            return Optional.empty();
        }

        @Override
        public Optional<String> findMessageIdByStep(String stepUid) {
            return Optional.empty();
        }

        @Override
        public void updateStepStatus(String stepUid, StepStatus status, int retryCount, String errorMessage, String outputText) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateStepApproval(String stepUid, ApprovalStatus approvalStatus, StepStatus stepStatus) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendEvent(ai.nomoclaw.bot.model.AgentEvent event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ai.nomoclaw.bot.model.AgentEvent> listEventsByMessage(String messageUid) {
            return List.of();
        }
    }

    private static final class FakeAgentDefinitionRepository extends AgentDefinitionRepository {
        private AgentDefinitionEntity agent;

        @Override
        public AgentDefinitionEntity findByUid(String agentUid) {
            return agent != null && "agent-1".equals(agentUid) ? agent : null;
        }

        @Override
        public AgentDefinitionEntity findActiveByUid(String agentUid) {
            return findByUid(agentUid);
        }
    }

    private static final class FakeAgentGroupDefinitionRepository extends AgentGroupDefinitionRepository {
        @Override
        public ai.nomoclaw.bot.store.entity.AgentGroupDefinitionEntity findActiveByUid(String agentGroupUid) {
            return null;
        }
    }

    private static final class FakeAgentGroupMemberRepository extends AgentGroupMemberRepository {
        @Override
        public ai.nomoclaw.bot.store.entity.AgentGroupMemberEntity findPrimaryByGroupUid(String groupUid) {
            return null;
        }
    }

    private static final class FakeAgentTipRepository extends AgentTipRepository {
        private AgentTipEntity savedTip;
        private AgentTipEntity updatedTip;

        @Override
        public List<AgentTipEntity> listActiveByAgentUid(String agentUid) {
            return List.of();
        }

        @Override
        public AgentTipEntity findByAgentUidAndTipUid(String agentUid, String tipUid) {
            return null;
        }

        @Override
        public AgentTipEntity findByAgentUidAndSourceMessageUid(String agentUid, String sourceMessageUid) {
            return null;
        }

        @Override
        public void deleteByAgentUid(String agentUid) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean save(AgentTipEntity tip) {
            savedTip = tip;
            return true;
        }

        @Override
        public boolean updateById(AgentTipEntity tip) {
            updatedTip = tip;
            return true;
        }
    }
}
