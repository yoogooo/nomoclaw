package ai.nomoclaw.bot.agent.channel;

import ai.nomoclaw.bot.channel.core.ChannelOrchestratorService;
import ai.nomoclaw.bot.channel.model.ChannelPolicy;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.platform.AbstractWebhookChannel;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class ChannelPolicyTests {

    @Test
    void shouldBlockWhenMentionRequiredButNotMentioned() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.FEISHU, orchestratorService, new ChannelPolicy(true, java.util.Set.of()));
        InboundEnvelope envelope = new InboundEnvelope(
                ChannelType.FEISHU,
                "tenant",
                "msg-1",
                "chat:1",
                "user-1",
                "hello",
                false,
                "https://example.com",
                Instant.now(),
                Map.of()
        );

        channel.consume(envelope);
        verify(orchestratorService, never()).processInbound(envelope);
    }

    @Test
    void shouldPassWhenMentionSatisfied() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.DINGTALK, orchestratorService, new ChannelPolicy(true, java.util.Set.of()));
        InboundEnvelope envelope = new InboundEnvelope(
                ChannelType.DINGTALK,
                "tenant",
                "msg-2",
                "conversation:2",
                "user-2",
                "hello",
                true,
                "https://example.com",
                Instant.now(),
                Map.of()
        );

        channel.consume(envelope);
        verify(orchestratorService, times(1)).processInbound(envelope);
    }

    private static class TestWebhookChannel extends AbstractWebhookChannel {

        private TestWebhookChannel(ChannelType type, ChannelOrchestratorService orchestratorService, ChannelPolicy policy) {
            super(type, orchestratorService, policy, HttpClient.newHttpClient());
        }

        @Override
        protected String buildPayload(String text) {
            return "{\"text\":\"" + text + "\"}";
        }
    }
}
