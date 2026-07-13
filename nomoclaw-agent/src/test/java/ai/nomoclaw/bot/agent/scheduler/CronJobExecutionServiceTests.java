package ai.nomoclaw.bot.agent.scheduler;

import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.notification.NotificationSender;
import ai.nomoclaw.bot.orchestrator.AgentApplicationService;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionRuntimeStateStore;
import ai.nomoclaw.bot.scheduler.CronChannelTargetResolver;
import ai.nomoclaw.bot.scheduler.CronJobExecutionService;
import ai.nomoclaw.bot.scheduler.CronJobSchedulerService;
import ai.nomoclaw.bot.scheduler.CronNotificationFanoutService;
import ai.nomoclaw.bot.scheduler.CronSubscriptionRepository;
import ai.nomoclaw.bot.scheduler.config.CronNotifyProperties;
import ai.nomoclaw.bot.store.AgentStore;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.entity.AgentCronJobExecutionEntity;
import ai.nomoclaw.bot.store.entity.AgentDefinitionEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobExecutionRepository;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.store.repository.AgentDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CronJobExecutionServiceTests {

    @Test
    void shouldPersistTerminalStatusEvenWhenNotificationFails() throws Exception {
        AgentCronJobRepository jobRepository = mock(AgentCronJobRepository.class);
        AgentCronJobExecutionRepository executionRepository = mock(AgentCronJobExecutionRepository.class);
        AgentApplicationService agentApplicationService = mock(AgentApplicationService.class);
        CronJobSchedulerService schedulerService = mock(CronJobSchedulerService.class);
        AgentDefinitionRepository agentDefinitionRepository = mock(AgentDefinitionRepository.class);
        AgentStore store = mock(AgentStore.class);
        ExecutionRuntimeStateStore runtimeStateStore = mock(ExecutionRuntimeStateStore.class);

        NotificationSender notificationSender = mock(NotificationSender.class);
        doThrow(new IllegalStateException("notification failed")).when(notificationSender).send(any());
        CronNotifyProperties notifyProperties = new CronNotifyProperties();
        notifyProperties.setRetryMax(1);
        notifyProperties.setRetryBackoffMs(1L);
        notifyProperties.setSummaryMaxLines(5);
        CronSubscriptionRepository subscriptionRepository = mock(CronSubscriptionRepository.class);
        when(subscriptionRepository.listEnabledByJobUid("job-1")).thenReturn(List.of(
                new CronSubscriptionRepository.CronSubscription(
                        "sub-1",
                        "job-1",
                        "dingtalk",
                        "dingtalk:session:https://example",
                        "",
                        true,
                        LocalDateTime.now(),
                        LocalDateTime.now()
                )
        ));
        CronChannelTargetResolver channelTargetResolver = mock(CronChannelTargetResolver.class);
        CronNotificationFanoutService notificationFanoutService = new CronNotificationFanoutService(
                notificationSender,
                subscriptionRepository,
                notifyProperties,
                channelTargetResolver
        );

        CronJobExecutionService service = new CronJobExecutionService(
                jobRepository,
                executionRepository,
                agentApplicationService,
                notificationFanoutService,
                schedulerService,
                agentDefinitionRepository,
                store,
                1800,
                60,
                runtimeStateStore
        );

        AgentCronJobExecutionEntity execution = new AgentCronJobExecutionEntity();
        execution.setExecutionUid("exec-1");
        execution.setJobUid("job-1");
        execution.setAgentUid("agent-1");
        execution.setConversationUid("");
        execution.setMessageUid("msg-1");
        execution.setStatus("RUNNING");
        execution.setStartedTime(LocalDateTime.now().minusMinutes(1));
        execution.setUpdatedTime(LocalDateTime.now().minusSeconds(10));

        AgentCronJobEntity job = new AgentCronJobEntity();
        job.setJobUid("job-1");
        job.setAgentUid("agent-1");
        job.setTitle("Test Job");
        job.setExpression("0 0/5 * * * ?");
        job.setTimezone("Asia/Shanghai");
        job.setTaskContent("Do work");
        job.setStatus("ACTIVE");

        AgentDefinitionEntity agent = new AgentDefinitionEntity();
        agent.setAgentUid("agent-1");
        agent.setAgentName("test-agent");
        Path workspace = Files.createTempDirectory("cron-job-execution-service-test");
        agent.setWorkspace(workspace.toString());

        AgentMessage message = new AgentMessage(
                "msg-1",
                "",
                "",
                "user",
                "done",
                MessageStatus.COMPLETED,
                "",
                "",
                0,
                0,
                0,
                0,
                Instant.now().minusSeconds(60),
                Instant.now()
        );

        when(executionRepository.listActiveForResume(10)).thenReturn(List.of(execution));
        when(agentApplicationService.getMessage("msg-1")).thenReturn(message);
        when(jobRepository.findByJobUid("job-1")).thenReturn(job);
        when(agentDefinitionRepository.findActiveByUid("agent-1")).thenReturn(agent);
        when(schedulerService.nextRunTime("job-1", "Asia/Shanghai")).thenReturn(LocalDateTime.now().plusMinutes(5));

        service.resumeActiveExecutions(10);

        verify(executionRepository).update(any());
        verify(jobRepository).update(any());
        verify(store).updateMessageStatus("msg-1", MessageStatus.COMPLETED);
    }
}
