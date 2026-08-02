package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.conversation.service.ConversationService;
import ai.nomoclaw.bot.conversation.support.ConversationAttachmentService;
import ai.nomoclaw.bot.domain.AgentMessage;
import ai.nomoclaw.bot.llm.codex.CodexUsageLimitException;
import ai.nomoclaw.bot.llm.config.LlmProperties;
import ai.nomoclaw.bot.model.MessageStatus;
import ai.nomoclaw.bot.orchestrator.execution.ExecutionScopeResolver;
import ai.nomoclaw.bot.orchestrator.execution.MessageExecutionOrchestrator;
import ai.nomoclaw.bot.orchestrator.execution.StepExecutionService;
import ai.nomoclaw.bot.orchestrator.view.ExecutionFeedbackBuilder;
import ai.nomoclaw.bot.planner.Planner;
import ai.nomoclaw.bot.policy.RiskPolicy;
import ai.nomoclaw.bot.policy.tool.ToolPermissionPolicyService;
import ai.nomoclaw.bot.store.AgentStore;
import dev.langchain4j.exception.AuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentApplicationServiceTests {

    @Test
    void shouldFormatFriendlyMessageForCodexUsageLimit() throws Exception {
        AgentApplicationService service = new AgentApplicationService(
                mock(AgentStore.class),
                mock(Planner.class),
                mock(RiskPolicy.class),
                mock(AgentEventBus.class),
                mock(MessageCancellationRegistry.class),
                new AgentProperties(),
                new LlmProperties(),
                mock(ConversationAttachmentService.class),
                mock(ToolExecutionPolicyGateway.class),
                mock(ToolPermissionPolicyService.class),
                mock(PermissionAppService.class),
                mock(ExecutionFeedbackBuilder.class),
                mock(StepExecutionService.class),
                mock(ExecutionScopeResolver.class),
                mock(MessageExecutionOrchestrator.class),
                mock(ApplicationEventPublisher.class),
                mock(ConversationService.class)
        );
        AgentMessage message = new AgentMessage(
                "msg-1",
                "conv-1",
                null,
                "user",
                "hello",
                MessageStatus.CREATED,
                "codex",
                "gpt-5.5",
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now()
        );
        Method method = AgentApplicationService.class.getDeclaredMethod(
                "toUserFriendlyFailureMessage",
                Throwable.class,
                AgentMessage.class
        );
        method.setAccessible(true);

        String friendlyMessage = (String) method.invoke(
                service,
                new CompletionException(new CodexUsageLimitException(
                        429,
                        "usage_limit_reached",
                        "plus",
                        "The usage limit has been reached",
                        1782908546L,
                        10271L
                )),
                message
        );

        assertEquals(
                "Codex 使用额度已用尽（套餐：plus），预计 2026-07-01 20:22:26 CST 后恢复。请稍后重试，或切换到可用的账号、套餐或模型提供商。",
                friendlyMessage
        );
    }

    @Test
    void shouldFormatFriendlyMessageForAuthenticationFailure() throws Exception {
        ExecutionFeedbackBuilder feedbackBuilder = mock(ExecutionFeedbackBuilder.class);
        when(feedbackBuilder.messageFailedAuthentication()).thenReturn(
                "模型服务认证失败，请检查 API Key 是否正确、是否已过期，以及模型服务地址配置是否匹配。"
        );
        AgentApplicationService service = new AgentApplicationService(
                mock(AgentStore.class),
                mock(Planner.class),
                mock(RiskPolicy.class),
                mock(AgentEventBus.class),
                mock(MessageCancellationRegistry.class),
                new AgentProperties(),
                new LlmProperties(),
                mock(ConversationAttachmentService.class),
                mock(ToolExecutionPolicyGateway.class),
                mock(ToolPermissionPolicyService.class),
                mock(PermissionAppService.class),
                feedbackBuilder,
                mock(StepExecutionService.class),
                mock(ExecutionScopeResolver.class),
                mock(MessageExecutionOrchestrator.class),
                mock(ApplicationEventPublisher.class),
                mock(ConversationService.class)
        );
        Method method = AgentApplicationService.class.getDeclaredMethod(
                "toUserFriendlyFailureMessage",
                Throwable.class,
                AgentMessage.class
        );
        method.setAccessible(true);

        String friendlyMessage = (String) method.invoke(
                service,
                new AuthenticationException("Incorrect API key provided"),
                null
        );

        assertEquals(
                "模型服务认证失败，请检查 API Key 是否正确、是否已过期，以及模型服务地址配置是否匹配。",
                friendlyMessage
        );
    }
}
