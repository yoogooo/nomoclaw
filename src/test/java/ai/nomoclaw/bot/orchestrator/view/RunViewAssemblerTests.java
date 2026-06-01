package ai.nomoclaw.bot.orchestrator.view;

import ai.nomoclaw.bot.config.I18nConfig;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.*;
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

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void shouldForceCanceledRunStatusFromMessageAndMarkLatestPendingStepCanceled() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        PlanStep step1 = new PlanStep(
                "step-1",
                1,
                1,
                "执行工具: MemorySearchTool",
                "MemorySearchTool",
                JsonNodeFactory.instance.objectNode().put("query", "abc"),
                RiskLevel.LOW,
                "",
                StepStatus.COMPLETED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
        PlanStep step2 = new PlanStep(
                "step-2",
                1,
                2,
                "打开网页",
                "BrowserTool",
                JsonNodeFactory.instance.objectNode().put("action", "navigate"),
                RiskLevel.LOW,
                "",
                StepStatus.COMPLETED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
        PlanStep step3 = new PlanStep(
                "step-3",
                1,
                3,
                "提取网页信息",
                "BrowserTool",
                JsonNodeFactory.instance.objectNode().put("action", "extract_text"),
                RiskLevel.LOW,
                "",
                StepStatus.COMPLETED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
        PlanStep step4 = new PlanStep(
                "step-4",
                1,
                4,
                "打开网页",
                "BrowserTool",
                JsonNodeFactory.instance.objectNode().put("action", "navigate"),
                RiskLevel.LOW,
                "",
                StepStatus.CREATED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
        AgentMessage message = new AgentMessage(
                "msg-3",
                "conv-3",
                null,
                "user",
                "hello",
                MessageStatus.CANCELED,
                "",
                "",
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );
        ObjectNode finished1 = JsonNodeFactory.instance.objectNode();
        finished1.put("stepUid", "step-1");
        finished1.put("roundIndex", 1);
        finished1.put("stepIndex", 1);
        finished1.put("status", "completed");
        AgentEvent event1 = new AgentEvent(UUID.randomUUID().toString(), AgentEventType.STEP_FINISHED, "conv-3", "msg-3", "step-1", Instant.now(), finished1);
        ObjectNode finished2 = JsonNodeFactory.instance.objectNode();
        finished2.put("stepUid", "step-2");
        finished2.put("roundIndex", 1);
        finished2.put("stepIndex", 2);
        finished2.put("status", "completed");
        AgentEvent event2 = new AgentEvent(UUID.randomUUID().toString(), AgentEventType.STEP_FINISHED, "conv-3", "msg-3", "step-2", Instant.now(), finished2);
        ObjectNode finished3 = JsonNodeFactory.instance.objectNode();
        finished3.put("stepUid", "step-3");
        finished3.put("roundIndex", 1);
        finished3.put("stepIndex", 3);
        finished3.put("status", "completed");
        AgentEvent event3 = new AgentEvent(UUID.randomUUID().toString(), AgentEventType.STEP_FINISHED, "conv-3", "msg-3", "step-3", Instant.now(), finished3);

        var run = assembler.toMessageRunResponse(message, List.of(step1, step2, step3, step4), List.of(event1, event2, event3));

        assertNotNull(run);
        assertEquals("canceled", run.status());
        assertEquals("canceled", run.steps().get(3).status());
    }
}
