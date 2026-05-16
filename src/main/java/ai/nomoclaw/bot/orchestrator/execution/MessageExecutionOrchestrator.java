package ai.nomoclaw.bot.orchestrator.execution;

import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Message 执行主循环编排器。
 *
 * <p>负责异步调度、轮次推进、执行终止条件收敛，以及运行态生命周期管理。
 */
@Component
@Slf4j
public class MessageExecutionOrchestrator {

    private final TaskExecutor taskExecutor;
    private final ExecutionRuntimeStateStore runtimeStateStore;

    public MessageExecutionOrchestrator(@Qualifier("agentTaskExecutor") TaskExecutor taskExecutor,
                                        ExecutionRuntimeStateStore runtimeStateStore) {
        this.taskExecutor = taskExecutor;
        this.runtimeStateStore = runtimeStateStore;
    }

    public void enqueue(String messageUid, Locale locale, String approvalMode, ExecutionDriver driver) {
        // approvalMode 在 submit 阶段写入，后续 round 执行时按 messageUid 读取。
        runtimeStateStore.setApprovalMode(messageUid, approvalMode);
        runAsync(messageUid, locale, driver);
    }

    public void resume(String messageUid, ExecutionDriver driver) {
        runAsync(messageUid, LocaleContextHolder.getLocale(), driver);
    }

    public String cancel(String messageUid) {
        String partial = runtimeStateStore.getBufferedAnswer(messageUid);
        runtimeStateStore.clearBufferedAnswer(messageUid);
        return partial == null ? "" : partial;
    }

    public boolean isRunning(String messageUid) {
        return runtimeStateStore.isRunning(messageUid);
    }

    public void clearRuntime(String messageUid) {
        runtimeStateStore.clear(messageUid);
    }

    public int currentRound(String messageUid, int defaultValue) {
        return runtimeStateStore.currentRound(messageUid, defaultValue);
    }

    public String approvalMode(String messageUid, String defaultValue) {
        return runtimeStateStore.getApprovalMode(messageUid, defaultValue);
    }

    private void runAsync(String messageUid, Locale locale, ExecutionDriver driver) {
        log.info("[Agent] queue message execution messageUid={}", messageUid);
        taskExecutor.execute(() -> {
            Locale previous = LocaleContextHolder.getLocale();
            try {
                LocaleContextHolder.setLocale(locale == null ? Locale.getDefault() : locale);
                execute(messageUid, driver);
            } finally {
                LocaleContextHolder.setLocale(previous);
            }
        });
    }

    private void execute(String messageUid, ExecutionDriver driver) {
        // 幂等保护：同一 messageUid 只允许一个执行实例进入主循环。
        if (!runtimeStateStore.start(messageUid)) {
            log.info("[Agent] message already running messageUid={}", messageUid);
            return;
        }
        try {
            AgentMessage message = driver.requireMessage(messageUid);
            MessageExecutionRuntimeState state = runtimeStateStore.getOrCreateState(
                    messageUid,
                    driver.buildConversationMemory(message)
            );
            while (state.currentRound() <= driver.maxRounds()) {
                message = driver.requireMessage(messageUid);
                if (driver.isCanceled(messageUid, message.status())) {
                    log.info("[Agent] stop execution because canceled messageUid={}", messageUid);
                    driver.cleanupCancellation(messageUid);
                    runtimeStateStore.clear(messageUid);
                    return;
                }
                driver.markMessageRunning(messageUid);
                log.info("[Agent] reasoning round messageUid={} conversationUid={} round={}/{} memorySize={}",
                        messageUid,
                        message.conversationUid(),
                        state.currentRound(),
                        driver.maxRounds(),
                        state.memory().size());

                List<PlanStep> roundSteps = driver.pendingStepsForRound(messageUid, state.currentRound());
                if (roundSteps.isEmpty()) {
                    RoundPlanningResult planningResult = driver.reasonNextAction(message, state, runtimeStateStore);
                    if (planningResult.completed()) {
                        if (driver.isCancellationRequested(message.messageUid())) {
                            driver.cleanupCancellation(messageUid);
                            runtimeStateStore.clear(messageUid);
                            return;
                        }
                        driver.completeMessage(message, planningResult.answer(), state.currentRound());
                        runtimeStateStore.clear(messageUid);
                        return;
                    }
                    roundSteps = planningResult.steps();
                }

                String approvalMode = runtimeStateStore.getApprovalMode(messageUid, driver.defaultApprovalMode());
                RoundExecutionResult executionResult = driver.executeRound(message, state, roundSteps, approvalMode);
                if (executionResult.waitingApproval()) {
                    // WAITING_APPROVAL 需要释放 running 标记，等待外部 approve/reject 触发 resume。
                    runtimeStateStore.finish(messageUid);
                    return;
                }
                if (executionResult.canceled()) {
                    driver.cleanupCancellation(messageUid);
                    runtimeStateStore.clear(messageUid);
                    return;
                }
                state.advanceRound();
            }
            driver.handleLoopLimitReached(messageUid, state);
            runtimeStateStore.clear(messageUid);
        } catch (Exception ex) {
            log.error("message execution failed messageUid={}", messageUid, ex);
            driver.handleExecutionFailure(messageUid, ex);
            runtimeStateStore.clear(messageUid);
        } finally {
            // finish 与 clear 分离：finish 仅移除 running 标记，clear 由终态路径统一负责。
            runtimeStateStore.finish(messageUid);
        }
    }

    public interface ExecutionDriver {

        AgentMessage requireMessage(String messageUid);

        List<ChatMessage> buildConversationMemory(AgentMessage initialMessage);

        int maxRounds();

        List<PlanStep> pendingStepsForRound(String messageUid, int roundIndex);

        RoundPlanningResult reasonNextAction(AgentMessage message,
                                             MessageExecutionRuntimeState state,
                                             ExecutionRuntimeStateStore runtimeStateStore);

        RoundExecutionResult executeRound(AgentMessage message,
                                          MessageExecutionRuntimeState state,
                                          List<PlanStep> steps,
                                          String approvalMode);

        boolean isCanceled(String messageUid, MessageStatus status);

        boolean isCancellationRequested(String messageUid);

        void cleanupCancellation(String messageUid);

        void markMessageRunning(String messageUid);

        void completeMessage(AgentMessage message, String answer, int roundsUsed);

        void handleLoopLimitReached(String messageUid, MessageExecutionRuntimeState state);

        void handleExecutionFailure(String messageUid, Exception ex);

        String defaultApprovalMode();
    }
}
