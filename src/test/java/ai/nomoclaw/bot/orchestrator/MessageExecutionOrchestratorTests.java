package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import dev.langchain4j.data.message.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageExecutionOrchestratorTests {

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
                                                     String approvalMode) {
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

        orchestrator.enqueue("msg-1", null, "default", driver);

        assertEquals(1, executeCount.get());
    }
}
