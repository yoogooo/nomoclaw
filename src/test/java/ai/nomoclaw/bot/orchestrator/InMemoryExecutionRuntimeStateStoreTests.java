package ai.nomoclaw.bot.orchestrator;

import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryExecutionRuntimeStateStoreTests {

    @Test
    void shouldStartAndClearMessageRuntimeState() {
        InMemoryExecutionRuntimeStateStore store = new InMemoryExecutionRuntimeStateStore();

        assertTrue(store.start("msg-1"));
        assertFalse(store.start("msg-1"));
        assertTrue(store.isRunning("msg-1"));

        store.setApprovalMode("msg-1", "full_access");
        store.appendDelta("msg-1", "hello");
        store.getOrCreateState("msg-1", List.of(UserMessage.from("hi"))).advanceRound();

        assertEquals("full_access", store.getApprovalMode("msg-1", "default"));
        assertEquals("hello", store.getBufferedAnswer("msg-1"));
        assertEquals(2, store.currentRound("msg-1", 1));

        store.clear("msg-1");

        assertFalse(store.isRunning("msg-1"));
        assertEquals("default", store.getApprovalMode("msg-1", "default"));
        assertEquals("", store.getBufferedAnswer("msg-1"));
        assertEquals(1, store.currentRound("msg-1", 1));
    }
}
