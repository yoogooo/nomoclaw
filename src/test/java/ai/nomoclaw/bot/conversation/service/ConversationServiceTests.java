package ai.nomoclaw.bot.conversation.service;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.conversation.support.ConversationAttachmentService;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScopeResolver;
import ai.nomoclaw.bot.orchestrator.execution.MessageExecutionOrchestrator;
import ai.nomoclaw.bot.orchestrator.view.RunViewAssembler;
import ai.nomoclaw.bot.store.AgentStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationServiceTests {

    @Test
    void listConversationsShouldUseLatestUserMessageStatusForRunning() {
        AgentStore store = mock(AgentStore.class);
        ConversationService service = new ConversationService(
                store,
                mock(AgentProperties.class),
                mock(MessageCancellationRegistry.class),
                mock(MessageExecutionOrchestrator.class),
                mock(ExecutionScopeResolver.class),
                mock(ConversationAttachmentService.class),
                mock(RunViewAssembler.class)
        );
        Instant now = Instant.now();
        AgentConversation conversation = new AgentConversation(
                "conv-1",
                "",
                "",
                "web",
                "测试下速度",
                false,
                0,
                0,
                0,
                0,
                now,
                now,
                now,
                now
        );
        AgentMessage latestCompletedMessage = new AgentMessage(
                "msg-2",
                "conv-1",
                "",
                "user",
                "测试下速度",
                MessageStatus.COMPLETED,
                "",
                "",
                0,
                0,
                0,
                0,
                now,
                now
        );

        when(store.listConversations()).thenReturn(List.of(conversation));
        when(store.findLatestUserMessageByConversation("conv-1")).thenReturn(Optional.of(latestCompletedMessage));

        assertFalse(service.listConversations().getFirst().running());
    }

    @Test
    void listConversationsShouldKeepRunningForLatestInProgressUserMessage() {
        AgentStore store = mock(AgentStore.class);
        ConversationService service = new ConversationService(
                store,
                mock(AgentProperties.class),
                mock(MessageCancellationRegistry.class),
                mock(MessageExecutionOrchestrator.class),
                mock(ExecutionScopeResolver.class),
                mock(ConversationAttachmentService.class),
                mock(RunViewAssembler.class)
        );
        Instant now = Instant.now();
        AgentConversation conversation = new AgentConversation(
                "conv-1",
                "",
                "",
                "web",
                "测试下速度",
                false,
                0,
                0,
                0,
                0,
                now,
                now,
                now,
                now
        );
        AgentMessage latestRunningMessage = new AgentMessage(
                "msg-3",
                "conv-1",
                "",
                "user",
                "测试下速度",
                MessageStatus.RUNNING,
                "",
                "",
                0,
                0,
                0,
                0,
                now,
                now
        );

        when(store.listConversations()).thenReturn(List.of(conversation));
        when(store.findLatestUserMessageByConversation("conv-1")).thenReturn(Optional.of(latestRunningMessage));

        assertTrue(service.listConversations().getFirst().running());
    }
}
