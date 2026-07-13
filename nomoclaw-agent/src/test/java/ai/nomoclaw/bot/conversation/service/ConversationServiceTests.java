package ai.nomoclaw.bot.conversation.service;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.conversation.model.ConversationMessageAnchorDto;
import ai.nomoclaw.bot.conversation.model.ConversationSearchPageDto;
import ai.nomoclaw.bot.conversation.support.ConversationAttachmentService;
import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScopeResolver;
import ai.nomoclaw.bot.orchestrator.execution.MessageExecutionOrchestrator;
import ai.nomoclaw.bot.orchestrator.view.RunViewAssembler;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.query.ConversationSearchRow;
import ai.nomoclaw.bot.store.query.PageSlice;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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

    @Test
    void searchConversationPageShouldReturnPreviewAndCursor() {
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
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        ConversationSearchRow row = new ConversationSearchRow();
        row.setConversationId(11L);
        row.setConversationUid("conv-1");
        row.setAgentGroupUid("group-1");
        row.setAgentUid("agent-1");
        row.setTitle("谷歌降价策略");
        row.setPreviewText("这是命中的正文内容");
        row.setResultTime(now);

        when(store.searchConversationPage(any()))
                .thenReturn(new PageSlice<>(List.of(row), false));

        ConversationSearchPageDto page = service.searchConversationPage("agent-1", "谷歌", 50, null);

        assertEquals(1, page.items().size());
        assertEquals("谷歌降价策略", page.items().getFirst().title());
        assertEquals("这是命中的正文内容", page.items().getFirst().previewText());
        assertNull(page.nextBeforeSortKey());
    }

    @Test
    void loadMessageAnchorShouldReturnAnchorWindow() {
        AgentStore store = mock(AgentStore.class);
        ConversationAttachmentService attachmentService = mock(ConversationAttachmentService.class);
        when(attachmentService.listByMessageUids(anyList())).thenReturn(Map.of());
        ConversationService service = new ConversationService(
                store,
                mock(AgentProperties.class),
                mock(MessageCancellationRegistry.class),
                mock(MessageExecutionOrchestrator.class),
                mock(ExecutionScopeResolver.class),
                attachmentService,
                mock(RunViewAssembler.class)
        );
        Instant now = Instant.now();
        AgentConversation conversation = new AgentConversation(
                "conv-1",
                "group-1",
                "agent-1",
                "web",
                "谷歌降价策略",
                false,
                0,
                0,
                0,
                0,
                now,
                now,
                now,
                now,
                now
        );
        AgentMessage older = new AgentMessage("msg-1", "conv-1", "", "user", "更早消息", MessageStatus.COMPLETED, "", "", 0, 0, 0, 0, now, now);
        AgentMessage anchor = new AgentMessage("msg-2", "conv-1", "", "assistant", "命中关键字的消息", MessageStatus.COMPLETED, "", "", 0, 0, 0, 0, now, now);
        AgentMessage newer = new AgentMessage("msg-3", "conv-1", "", "assistant", "更新消息", MessageStatus.COMPLETED, "", "", 0, 0, 0, 0, now, now);

        when(store.findConversation("conv-1")).thenReturn(Optional.of(conversation));
        when(store.findLatestMatchingMessage("conv-1", "%关键字%")).thenReturn(Optional.of(anchor));
        when(store.findMessageSortId("msg-2")).thenReturn(Optional.of(2L));
        when(store.listMessagesBeforeOrAt("conv-1", 2L, 32)).thenReturn(List.of(anchor, older));
        when(store.listMessagesAfter("conv-1", 2L, 20)).thenReturn(List.of(newer));

        ConversationMessageAnchorDto anchorDto = service.loadMessageAnchor("conv-1", "关键字", 30, 20);

        assertEquals("msg-2", anchorDto.anchorMessageUid());
        assertEquals(List.of("msg-1", "msg-2", "msg-3"), anchorDto.items().stream().map(item -> item.messageUid()).toList());
        assertFalse(anchorDto.hasMoreBefore());
    }
}
