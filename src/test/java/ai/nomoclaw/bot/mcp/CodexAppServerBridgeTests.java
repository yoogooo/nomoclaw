package ai.nomoclaw.bot.mcp;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.store.entity.AgentEventEntity;
import ai.nomoclaw.bot.store.repository.AgentEventRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodexAppServerBridgeTests {

    @Test
    void shouldResolveThreadIdFromConversationHistory() {
        AgentEventRepository agentEventRepository = mock(AgentEventRepository.class);
        CodexAppServerBridge bridge = new CodexAppServerBridge(agentEventRepository);
        when(agentEventRepository.listByConversationUid("conv-1")).thenReturn(List.of(
                event("{\"artifacts\":{\"originalToolName\":\"codex\",\"threadId\":\"thread-old\"}}"),
                event("{\"artifacts\":{\"originalToolName\":\"codex-reply\",\"threadId\":\"thread-new\"}}")
        ));

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("conversationId", "conv-1");

        String resolved = bridge.resolveTargetThreadId(toolRequest("conv-local", args));

        assertEquals("thread-new", resolved);
    }

    @Test
    void shouldPreferExplicitThreadIdOverConversationHistory() {
        AgentEventRepository agentEventRepository = mock(AgentEventRepository.class);
        CodexAppServerBridge bridge = new CodexAppServerBridge(agentEventRepository);

        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("threadId", "thread-explicit");
        args.put("conversationId", "conv-1");

        String resolved = bridge.resolveTargetThreadId(toolRequest("conv-local", args));

        assertEquals("thread-explicit", resolved);
    }

    @Test
    void shouldFallbackToCurrentConversationUidWhenConversationIdMissing() {
        AgentEventRepository agentEventRepository = mock(AgentEventRepository.class);
        CodexAppServerBridge bridge = new CodexAppServerBridge(agentEventRepository);
        when(agentEventRepository.listByConversationUid("conv-current")).thenReturn(List.of(
                event("{\"artifacts\":{\"originalToolName\":\"codex\",\"threadId\":\"thread-current\"}}")
        ));

        String resolved = bridge.resolveTargetThreadId(toolRequest("conv-current", JsonNodeFactory.instance.objectNode()));

        assertEquals("thread-current", resolved);
    }

    private ToolRequest toolRequest(String conversationUid, ObjectNode args) {
        return new ToolRequest(
                conversationUid,
                "msg-1",
                "step-1",
                "agent-1",
                "agent",
                Path.of("/tmp"),
                Path.of("/tmp"),
                Path.of("/tmp"),
                args,
                10_000L,
                null
        );
    }

    private AgentEventEntity event(String payload) {
        AgentEventEntity entity = new AgentEventEntity();
        entity.setPayload(payload);
        return entity;
    }
}
