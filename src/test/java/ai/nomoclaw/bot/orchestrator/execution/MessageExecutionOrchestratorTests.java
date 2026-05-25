package ai.nomoclaw.bot.orchestrator.execution;

import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageExecutionOrchestratorTests {

    @Test
    void shouldUpdateApprovalModeForActiveMessagesInConversation() {
        InMemoryExecutionRuntimeStateStore store = new InMemoryExecutionRuntimeStateStore();
        MessageExecutionOrchestrator orchestrator = new MessageExecutionOrchestrator(new SyncTaskExecutor(), store);
        store.bindConversation("msg-1", "conv-1");
        store.bindConversation("msg-2", "conv-1");
        store.bindConversation("msg-3", "conv-2");
        store.setApprovalMode("msg-1", "default");
        store.setApprovalMode("msg-2", "default");
        store.setApprovalMode("msg-3", "default");
        store.start("msg-1");
        store.getOrCreateState("msg-2", List.of());
        store.start("msg-3");

        orchestrator.updateApprovalModeForConversation("conv-1", "full_access");

        assertEquals("full_access", store.getApprovalMode("msg-1", "default"));
        assertEquals("full_access", store.getApprovalMode("msg-2", "default"));
        assertEquals("default", store.getApprovalMode("msg-3", "default"));
    }

    @Test
    void shouldRunOnlyOnceWhenNestedResumeOnSameMessage() {
        MessageExecutionOrchestrator orchestrator = new MessageExecutionOrchestrator(
                new SyncTaskExecutor(),
                new InMemoryExecutionRuntimeStateStore()
        );

        AtomicInteger executeCount = new AtomicInteger();
        AgentMessage message = new AgentMessage(
                "msg-1",
                "conv-1",
                null,
                "user",
                "hello",
                MessageStatus.CREATED,
                "",
                "",
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );

        MessageExecutionOrchestrator.ExecutionDriver driver = new MessageExecutionOrchestrator.ExecutionDriver() {
            @Override
            public AgentMessage requireMessage(String messageUid) {
                return message;
            }

            @Override
            public List<ChatMessage> buildConversationMemory(AgentMessage initialMessage) {
                return List.of();
            }

            @Override
            public int maxRounds() {
                return 1;
            }

            @Override
            public List<PlanStep> pendingStepsForRound(String messageUid, int roundIndex) {
                return List.of();
            }

            @Override
            public RoundPlanningResult reasonNextAction(AgentMessage message,
                                                        MessageExecutionRuntimeState state,
                                                        ExecutionRuntimeStateStore runtimeStateStore) {
                executeCount.incrementAndGet();
                orchestrator.resume(message.messageUid(), this);
                return RoundPlanningResult.completed("done");
            }

            @Override
            public RoundExecutionResult executeRound(AgentMessage message,
                                                     MessageExecutionRuntimeState state,
                                                     List<PlanStep> steps,
                                                     Supplier<String> approvalModeSupplier) {
                return RoundExecutionResult.success();
            }

            @Override
            public boolean isCanceled(String messageUid, MessageStatus status) {
                return false;
            }

            @Override
            public boolean isCancellationRequested(String messageUid) {
                return false;
            }

            @Override
            public void cleanupCancellation(String messageUid) {
            }

            @Override
            public void markMessageRunning(String messageUid) {
            }

            @Override
            public void completeMessage(AgentMessage message, String answer, int roundsUsed) {
            }

            @Override
            public void handleLoopLimitReached(String messageUid, MessageExecutionRuntimeState state) {
            }

            @Override
            public void handleExecutionFailure(String messageUid, Exception ex) {
            }

            @Override
            public String defaultApprovalMode() {
                return "default";
            }
        };

        orchestrator.enqueue("msg-1", "conv-1", null, "default", driver);

        assertEquals(1, executeCount.get());
    }
}
