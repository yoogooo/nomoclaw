package ai.nomoclaw.bot.orchestrator.view;

import ai.nomoclaw.bot.config.I18nConfig;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.AgentEvent;
import ai.nomoclaw.bot.model.AgentEventType;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.RiskLevel;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.util.LocalizedMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunViewAssemblerTests {

    private final MessageSource messageSource = new I18nConfig().messageSource();
    private final LocalizedMessages localizedMessages = new LocalizedMessages(messageSource);
    private final ExecutionFeedbackBuilder feedbackBuilder = new ExecutionFeedbackBuilder(localizedMessages);
    private final RunViewAssembler assembler = new RunViewAssembler(feedbackBuilder);

    @AfterEach
    void cleanupLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void shouldAssembleRunningStatusFromStepStartedEvent() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        PlanStep step = new PlanStep(
                "step-1",
                1,
                1,
                "执行命令: echo hi",
                "CommandTool",
                JsonNodeFactory.instance.objectNode().put("command", "echo hi"),
                RiskLevel.LOW,
                "",
                StepStatus.CREATED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
        AgentMessage message = new AgentMessage(
                "msg-1",
                "conv-1",
                null,
                "user",
                "hello",
                MessageStatus.RUNNING,
                "",
                "",
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );

        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("stepUid", "step-1");
        payload.put("roundIndex", 1);
        payload.put("stepIndex", 1);
        payload.put("status", "running");
        payload.put("toolName", "CommandTool");
        payload.set("toolArgs", JsonNodeFactory.instance.objectNode().put("command", "echo hi"));
        payload.put("displayTitle", "正在执行命令: echo hi");
        payload.put("displaySummary", "正在执行");
        payload.put("displayDetails", "系统正在执行本地命令“echo hi”。");

        AgentEvent started = new AgentEvent(
                UUID.randomUUID().toString(),
                AgentEventType.STEP_STARTED,
                "conv-1",
                "msg-1",
                "step-1",
                Instant.now(),
                payload
        );

        var run = assembler.toMessageRunResponse(message, List.of(step), List.of(started));

        assertNotNull(run);
        assertEquals("running", run.status());
        assertEquals(0, run.completedSteps());
        assertEquals(1, run.totalSteps());
        assertEquals("正在处理，已完成 0/1 步", run.summary());
        assertEquals("running", run.steps().get(0).status());
    }

    @Test
    void shouldInjectReasoningVirtualStepBeforeToolStepsInSameRound() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        PlanStep step = new PlanStep(
                "step-tool-1",
                2,
                1,
                "执行命令: ls",
                "CommandTool",
                JsonNodeFactory.instance.objectNode().put("command", "ls"),
                RiskLevel.LOW,
                "",
                StepStatus.CREATED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
        AgentMessage message = new AgentMessage(
                "msg-2",
                "conv-2",
                null,
                "user",
                "hello",
                MessageStatus.RUNNING,
                "",
                "",
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );

        ObjectNode reasoningPayload = JsonNodeFactory.instance.objectNode();
        reasoningPayload.put("roundIndex", 2);
        reasoningPayload.put("content", "先思考再执行。");
        AgentEvent reasoningEvent = new AgentEvent(
                UUID.randomUUID().toString(),
                AgentEventType.MESSAGE_REASONING,
                "conv-2",
                "msg-2",
                "",
                Instant.now(),
                reasoningPayload
        );

        ObjectNode startedPayload = JsonNodeFactory.instance.objectNode();
        startedPayload.put("stepUid", "step-tool-1");
        startedPayload.put("roundIndex", 2);
        startedPayload.put("stepIndex", 1);
        startedPayload.put("status", "running");
        startedPayload.put("toolName", "CommandTool");
        startedPayload.set("toolArgs", JsonNodeFactory.instance.objectNode().put("command", "ls"));
        startedPayload.put("displayTitle", "正在执行命令: ls");
        startedPayload.put("displaySummary", "正在执行");
        startedPayload.put("displayDetails", "系统正在执行本地命令“ls”。");
        AgentEvent startedEvent = new AgentEvent(
                UUID.randomUUID().toString(),
                AgentEventType.STEP_STARTED,
                "conv-2",
                "msg-2",
                "step-tool-1",
                Instant.now(),
                startedPayload
        );

        var run = assembler.toMessageRunResponse(message, List.of(step), List.of(reasoningEvent, startedEvent));

        assertNotNull(run);
        assertEquals(2, run.steps().size());
        assertEquals(0, run.steps().get(0).stepIndex());
        assertEquals("Reasoning", run.steps().get(0).toolName());
        assertTrue(run.steps().get(0).displayDetails().contains("先思考再执行"));
        assertEquals("step-tool-1", run.steps().get(1).stepUid());
    }
}
