package ai.nomoclaw.bot.agent.channel;

import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.channel.core.ChannelOrchestratorService;
import ai.nomoclaw.bot.channel.core.ChannelPendingReplyContextStore;
import ai.nomoclaw.bot.channel.model.ChannelSessionKey;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.conversation.model.ConversationAttachmentDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.conversation.model.MessageFileLinkDto;
import ai.nomoclaw.bot.channel.repository.ChannelInboundDedupRepository;
import ai.nomoclaw.bot.channel.spi.ChannelMessageRouter;
import ai.nomoclaw.bot.channel.spi.ChannelSessionRepository;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.modelconfig.ModelConfigAppService;
import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

class ChannelOrchestratorServiceTests {

    @Test
    void shouldResetSessionAndSendAckWhenReceivingNewConversationCommand() {
        TestAgentApplicationService agentApplicationService = new TestAgentApplicationService();
        InMemoryChannelSessionRepository channelSessionRepository = new InMemoryChannelSessionRepository();
        TestChannelInboundDedupRepository dedupRepository = new TestChannelInboundDedupRepository();
        RecordingChannelMessageRouter channelMessageRouter = new RecordingChannelMessageRouter();
        AgentChannelsProperties channelProperties = new AgentChannelsProperties();
        ChannelPendingReplyContextStore pendingReplyContextStore = new ChannelPendingReplyContextStore();
        TestModelConfigAppService modelConfigAppService = new TestModelConfigAppService();
        modelConfigAppService.availableModelConfig = availableConfig("deepseek", "deepseek-v4-flash");
        ChannelOrchestratorService service = new ChannelOrchestratorService(
                agentApplicationService,
                channelSessionRepository,
                dedupRepository,
                channelMessageRouter,
                channelProperties,
                pendingReplyContextStore,
                modelConfigAppService
        );
        InboundEnvelope envelope = envelope("/new");
        channelSessionRepository.upsert(new ChannelSessionRepository.ChannelSessionRecord(
                envelope.toSessionKey(),
                "conv-old",
                "reply-1",
                Map.of("chatType", "p2p")
        ));
        agentApplicationService.nextConversationUid = "conv-new";

        service.processInbound(envelope);

        Assertions.assertTrue(dedupRepository.saved);
        Assertions.assertEquals("agent_general_assistant", agentApplicationService.lastCreateConversation.agentUid);
        Assertions.assertEquals("conv-new", channelSessionRepository.find(envelope.toSessionKey()).orElseThrow().conversationUid());
        Assertions.assertEquals("conv-new", channelMessageRouter.lastMetadata.get("conversationUid"));
        Assertions.assertEquals("new_conversation", channelMessageRouter.lastMetadata.get("phase"));
        Assertions.assertEquals("已开启新对话，默认模型为 deepseek/deepseek-v4-flash。", channelMessageRouter.lastText);
        Assertions.assertNull(agentApplicationService.lastSubmit);
    }

    @Test
    void shouldSubmitFirstMessageToDefaultDeepseekModelWhenUsingNewConversationCommandWithText() {
        TestAgentApplicationService agentApplicationService = new TestAgentApplicationService();
        InMemoryChannelSessionRepository channelSessionRepository = new InMemoryChannelSessionRepository();
        TestChannelInboundDedupRepository dedupRepository = new TestChannelInboundDedupRepository();
        RecordingChannelMessageRouter channelMessageRouter = new RecordingChannelMessageRouter();
        AgentChannelsProperties channelProperties = new AgentChannelsProperties();
        channelProperties.setProcessingAckEnabled(false);
        ChannelPendingReplyContextStore pendingReplyContextStore = new ChannelPendingReplyContextStore();
        pendingReplyContextStore.startCleanupLoop();
        TestModelConfigAppService modelConfigAppService = new TestModelConfigAppService();
        modelConfigAppService.availableModelConfig = availableConfig("deepseek", "deepseek-v4-flash");
        ChannelOrchestratorService service = new ChannelOrchestratorService(
                agentApplicationService,
                channelSessionRepository,
                dedupRepository,
                channelMessageRouter,
                channelProperties,
                pendingReplyContextStore,
                modelConfigAppService
        );
        InboundEnvelope envelope = envelope("/new 帮我总结一下今天的站会");
        agentApplicationService.nextConversationUid = "conv-new";

        try {
            service.processInbound(envelope);
        } finally {
            pendingReplyContextStore.destroy();
        }

        Assertions.assertTrue(dedupRepository.saved);
        Assertions.assertNotNull(agentApplicationService.lastSubmit);
        Assertions.assertEquals("agent_general_assistant", agentApplicationService.lastCreateConversation.agentUid);
        Assertions.assertEquals("conv-new", agentApplicationService.lastSubmit.conversationUid);
        Assertions.assertEquals("帮我总结一下今天的站会", agentApplicationService.lastSubmit.message);
        Assertions.assertEquals(List.of(), agentApplicationService.lastSubmit.fileUrls);
        Assertions.assertEquals("deepseek", agentApplicationService.lastSubmit.modelProvider);
        Assertions.assertEquals("deepseek-v4-flash", agentApplicationService.lastSubmit.modelName);
        Assertions.assertEquals("default", agentApplicationService.lastSubmit.approvalMode);
        Assertions.assertEquals("feishu", agentApplicationService.lastSubmit.channel);
        Assertions.assertNotNull(agentApplicationService.lastSubmit.beforeExecuteHook);
        Assertions.assertNull(channelMessageRouter.lastText);
    }

    @Test
    void shouldFallbackToPreviousConversationModelWhenDeepseekFlashIsUnavailable() {
        TestAgentApplicationService agentApplicationService = new TestAgentApplicationService();
        InMemoryChannelSessionRepository channelSessionRepository = new InMemoryChannelSessionRepository();
        TestChannelInboundDedupRepository dedupRepository = new TestChannelInboundDedupRepository();
        RecordingChannelMessageRouter channelMessageRouter = new RecordingChannelMessageRouter();
        AgentChannelsProperties channelProperties = new AgentChannelsProperties();
        channelProperties.setProcessingAckEnabled(false);
        ChannelPendingReplyContextStore pendingReplyContextStore = new ChannelPendingReplyContextStore();
        pendingReplyContextStore.startCleanupLoop();
        TestModelConfigAppService modelConfigAppService = new TestModelConfigAppService();
        modelConfigAppService.availableModelConfig = availableConfig("openai", "gpt-4o");
        ChannelOrchestratorService service = new ChannelOrchestratorService(
                agentApplicationService,
                channelSessionRepository,
                dedupRepository,
                channelMessageRouter,
                channelProperties,
                pendingReplyContextStore,
                modelConfigAppService
        );
        InboundEnvelope envelope = envelope("/new 写一个发布公告");
        channelSessionRepository.upsert(new ChannelSessionRepository.ChannelSessionRecord(
                envelope.toSessionKey(),
                "conv-old",
                "reply-1",
                Map.of("chatType", "p2p")
        ));
        agentApplicationService.nextConversationUid = "conv-new";
        agentApplicationService.messagesByConversation.put("conv-old", List.of(
                userMessage("openai", "gpt-4o"),
                userMessage("deepseek", "deepseek-v4-flash")
        ));

        try {
            service.processInbound(envelope);
        } finally {
            pendingReplyContextStore.destroy();
        }

        Assertions.assertNotNull(agentApplicationService.lastSubmit);
        Assertions.assertEquals("agent_general_assistant", agentApplicationService.lastCreateConversation.agentUid);
        Assertions.assertEquals("openai", agentApplicationService.lastSubmit.modelProvider);
        Assertions.assertEquals("gpt-4o", agentApplicationService.lastSubmit.modelName);
    }

    private InboundEnvelope envelope(String text) {
        return new InboundEnvelope(
                ChannelType.FEISHU,
                "tenant-1",
                "msg-1",
                "session-1",
                "user-1",
                text,
                false,
                "reply-1",
                Instant.parse("2026-06-17T00:00:00Z"),
                Map.of("chatType", "p2p")
        );
    }

    private ConversationMessageDto userMessage(String provider, String modelName) {
        return new ConversationMessageDto(
                "msg-" + provider + "-" + modelName,
                null,
                "user",
                "hello",
                MessageStatus.CREATED,
                provider,
                modelName,
                0,
                0,
                0,
                0,
                Instant.parse("2026-06-17T00:00:00Z"),
                List.<MessageFileLinkDto>of(),
                List.<ConversationAttachmentDto>of()
        );
    }

    private ModelConfigDto availableConfig(String providerId, String modelId) {
        return new ModelConfigDto(List.of(
                new ModelConfigDto.Provider(
                        providerId,
                        providerId,
                        "OpenAI Compatible",
                        false,
                        true,
                        false,
                        "",
                        "",
                        true,
                        "configured",
                        "",
                        modelId,
                        List.of(new ModelConfigDto.Model(
                                modelId,
                                modelId,
                                List.of("text"),
                                false,
                                0,
                                0,
                                0,
                                new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, 0L, 0L, false, false),
                                true,
                                ""
                        ))
                )
        ));
    }

    private static final class TestAgentApplicationService extends AgentApplicationService {

        private String nextConversationUid = "";
        private CreateConversationCall lastCreateConversation;
        private SubmitCall lastSubmit;
        private final Map<String, List<ConversationMessageDto>> messagesByConversation = new HashMap<>();

        private TestAgentApplicationService() {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public String createConversation(String agentGroupUid, String agentUid, String channel) {
            lastCreateConversation = new CreateConversationCall(agentGroupUid, agentUid, channel);
            return nextConversationUid;
        }

        @Override
        public List<ConversationMessageDto> listMessages(String conversationUid) {
            return messagesByConversation.getOrDefault(conversationUid, List.of());
        }

        @Override
        public String submitMessage(String conversationUid,
                                    String message,
                                    List<String> fileUrls,
                                    String modelProvider,
                                    String modelName,
                                    String approvalMode,
                                    String channel,
                                    Consumer<String> beforeExecuteHook) {
            lastSubmit = new SubmitCall(
                    conversationUid,
                    message,
                    List.copyOf(fileUrls),
                    modelProvider,
                    modelName,
                    approvalMode,
                    channel,
                    beforeExecuteHook
            );
            return "msg-1";
        }
    }

    private record CreateConversationCall(
            String agentGroupUid,
            String agentUid,
            String channel
    ) {
    }

    private static final class TestModelConfigAppService extends ModelConfigAppService {

        private ModelConfigDto availableModelConfig = new ModelConfigDto(List.of());

        private TestModelConfigAppService() {
            super(null, null, null, null, null);
        }

        @Override
        public ModelConfigDto getAvailableModelConfig() {
            return availableModelConfig;
        }
    }

    private record SubmitCall(
            String conversationUid,
            String message,
            List<String> fileUrls,
            String modelProvider,
            String modelName,
            String approvalMode,
            String channel,
            Consumer<String> beforeExecuteHook
    ) {
    }

    private static final class InMemoryChannelSessionRepository implements ChannelSessionRepository {

        private final Map<ChannelSessionKey, ChannelSessionRecord> records = new HashMap<>();

        @Override
        public Optional<ChannelSessionRecord> find(ChannelSessionKey key) {
            return Optional.ofNullable(records.get(key));
        }

        @Override
        public ChannelSessionRecord upsert(ChannelSessionRecord record) {
            records.put(record.key(), record);
            return record;
        }
    }

    private static final class TestChannelInboundDedupRepository extends ChannelInboundDedupRepository {

        private boolean saved;

        private TestChannelInboundDedupRepository() {
            super(null);
        }

        @Override
        public boolean exists(InboundEnvelope envelope) {
            return false;
        }

        @Override
        public void save(InboundEnvelope envelope) {
            saved = true;
        }
    }

    private static final class RecordingChannelMessageRouter implements ChannelMessageRouter {

        private String lastText;
        private Map<String, String> lastMetadata;

        @Override
        public void send(ChannelType channel, String target, String text, Map<String, String> metadata) {
            this.lastText = text;
            this.lastMetadata = metadata;
        }
    }
}
