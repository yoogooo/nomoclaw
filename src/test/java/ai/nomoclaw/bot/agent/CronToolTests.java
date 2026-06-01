package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.scheduler.CronJobSchedulerService;
import ai.nomoclaw.bot.scheduler.CronSubscriptionRepository;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.tool.CronCreateTool;
import ai.nomoclaw.bot.tool.CronDeleteTool;
import ai.nomoclaw.bot.tool.CronListTool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CronToolTests {

    @Test
    void shouldPersistCronJobBeforeScheduling() {
        AgentCronJobRepository repository = mock(AgentCronJobRepository.class);
        CronJobSchedulerService schedulerService = mock(CronJobSchedulerService.class);
        when(schedulerService.scheduleJob(any())).thenReturn(java.time.LocalDateTime.of(2026, 3, 17, 6, 0));

        CronCreateTool cronTool = new CronCreateTool(repository, schedulerService);
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("expression", "*/5 * * * *");
        args.put("timezone", "Asia/Shanghai");
        args.put("task", "ping");

        Path workspace = Path.of("/tmp/demo-agent");
        var result = cronTool.execute(new ToolRequest(
                "conv",
                "msg",
                "step",
                "agent_demo",
                "demo",
                workspace,
                workspace.resolve("tmp"),
                workspace.resolve("report"),
                args,
                1000,
                progress -> {
                }
        ));

        ArgumentCaptor<AgentCronJobEntity> captor = ArgumentCaptor.forClass(AgentCronJobEntity.class);
        verify(repository).save(captor.capture());
        AgentCronJobEntity saved = captor.getValue();
        assertEquals("CronCreateTool", cronTool.name());
        assertEquals("agent_demo", saved.getAgentUid());
        assertEquals("0 */5 * * * ?", saved.getExpression());
        assertEquals("ACTIVE", saved.getStatus());
        assertTrue(result.success());
        assertEquals("ACTIVE", result.artifacts().path("status").asString());
        assertTrue(!result.artifacts().path("jobUid").asString("").isBlank());
    }

    @Test
    void shouldAcceptSevenFieldCronForOneTimeSchedule() {
        AgentCronJobRepository repository = mock(AgentCronJobRepository.class);
        CronJobSchedulerService schedulerService = mock(CronJobSchedulerService.class);
        when(schedulerService.scheduleJob(any())).thenReturn(java.time.LocalDateTime.of(2099, 4, 26, 22, 36));

        CronCreateTool cronTool = new CronCreateTool(repository, schedulerService);
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("expression", "0 36 22 26 4 ? 2099");
        args.put("timezone", "Asia/Shanghai");
        args.put("task", "one time reminder");

        var result = cronTool.execute(request(args));

        ArgumentCaptor<AgentCronJobEntity> captor = ArgumentCaptor.forClass(AgentCronJobEntity.class);
        verify(repository).save(captor.capture());
        AgentCronJobEntity saved = captor.getValue();
        assertTrue(result.success());
        assertEquals("0 36 22 26 4 ? 2099", saved.getExpression());
        assertEquals("0 36 22 26 4 ? 2099", result.artifacts().path("expression").asString());
    }

    @Test
    void shouldDeleteCronJobAndSubscriptions() {
        AgentCronJobRepository repository = mock(AgentCronJobRepository.class);
        CronJobSchedulerService schedulerService = mock(CronJobSchedulerService.class);
        CronSubscriptionRepository subscriptionRepository = mock(CronSubscriptionRepository.class);

        AgentCronJobEntity job = cronJob("job-1", "daily ping", "ACTIVE");
        job.setId(42L);
        when(repository.findByJobUid("job-1")).thenReturn(job);

        CronDeleteTool tool = new CronDeleteTool(repository, schedulerService, subscriptionRepository);
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("jobUid", "job-1");

        var result = tool.execute(request(args));

        assertTrue(result.success());
        assertEquals("CronDeleteTool", tool.name());
        assertEquals("job-1", result.artifacts().path("jobUid").asString());
        assertTrue(result.artifacts().path("deleted").asBoolean(false));
        verify(schedulerService).deleteJob("job-1");
        verify(subscriptionRepository).deleteByJobUid("job-1");
        verify(repository).removeById(42L);
    }

    @Test
    void shouldListCronJobsAndQueryByJobUid() {
        AgentCronJobRepository repository = mock(AgentCronJobRepository.class);
        AgentCronJobEntity active = cronJob("job-active", "active task", "ACTIVE");
        AgentCronJobEntity paused = cronJob("job-paused", "paused task", "PAUSED");
        when(repository.listAllJobs()).thenReturn(List.of(active, paused));
        when(repository.findByJobUid("job-paused")).thenReturn(paused);

        CronListTool tool = new CronListTool(repository);

        ObjectNode listArgs = JsonNodeFactory.instance.objectNode();
        listArgs.put("status", "ACTIVE");
        var listResult = tool.execute(request(listArgs));

        assertTrue(listResult.success());
        assertEquals("CronListTool", tool.name());
        assertEquals(1, listResult.artifacts().path("count").asInt());
        assertEquals("job-active", listResult.artifacts().path("jobs").get(0).path("jobUid").asString());
        assertEquals("active task", listResult.artifacts().path("jobs").get(0).path("task").asString());

        ObjectNode getArgs = JsonNodeFactory.instance.objectNode();
        getArgs.put("jobUid", "job-paused");
        var getResult = tool.execute(request(getArgs));

        assertTrue(getResult.success());
        assertEquals(1, getResult.artifacts().path("count").asInt());
        assertEquals("job-paused", getResult.artifacts().path("jobs").get(0).path("jobUid").asString());
        assertEquals("0 0 6 * * *", getResult.artifacts().path("jobs").get(0).path("expression").asString());
    }

    private ToolRequest request(ObjectNode args) {
        Path workspace = Path.of("/tmp/demo-agent");
        return new ToolRequest(
                "conv",
                "msg",
                "step",
                "agent_demo",
                "demo",
                workspace,
                workspace.resolve("tmp"),
                workspace.resolve("report"),
                args,
                1000,
                progress -> {
                }
        );
    }

    private AgentCronJobEntity cronJob(String jobUid, String task, String status) {
        AgentCronJobEntity job = new AgentCronJobEntity();
        job.setId(1L);
        job.setJobUid(jobUid);
        job.setAgentUid("agent_demo");
        job.setTitle(task);
        job.setExpression("0 0 6 * * *");
        job.setTimezone("Asia/Shanghai");
        job.setTaskContent(task);
        job.setStatus(status);
        job.setLastRunTime(LocalDateTime.of(2026, 3, 16, 6, 0));
        job.setNextRunTime(LocalDateTime.of(2026, 3, 17, 6, 0));
        job.setLastResult("OK");
        job.setCreatedTime(LocalDateTime.of(2026, 3, 15, 6, 0));
        job.setUpdatedTime(LocalDateTime.of(2026, 3, 16, 6, 0));
        return job;
    }
}
