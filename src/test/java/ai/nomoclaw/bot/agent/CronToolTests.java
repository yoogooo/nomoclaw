package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.scheduler.CronJobSchedulerService;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.store.repository.AgentCronJobRepository;
import ai.nomoclaw.bot.tool.CronTool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CronToolTests {

    @Test
    void shouldPersistCronJobBeforeScheduling() {
        AgentCronJobRepository repository = mock(AgentCronJobRepository.class);
        CronJobSchedulerService schedulerService = mock(CronJobSchedulerService.class);
        when(schedulerService.scheduleJob(any())).thenReturn(java.time.LocalDateTime.of(2026, 3, 17, 6, 0));

        CronTool cronTool = new CronTool(repository, schedulerService);
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
        assertEquals("agent_demo", saved.getAgentUid());
        assertEquals("0 */5 * * * *", saved.getExpression());
        assertEquals("ACTIVE", saved.getStatus());
        assertTrue(result.success());
        assertEquals("ACTIVE", result.artifacts().path("status").asText());
        assertTrue(!result.artifacts().path("jobUid").asText("").isBlank());
    }
}
