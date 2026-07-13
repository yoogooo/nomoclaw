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
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class ChannelPolicyTests {

    @Test
    void shouldBlockWhenMentionRequiredButNotMentioned() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.FEISHU, orchestratorService, new ChannelPolicy(true, Set.of()));
        InboundEnvelope envelope = envelope(ChannelType.FEISHU, false, Map.of());

        channel.consume(envelope);
        verify(orchestratorService, never()).processInbound(envelope);
    }

    @Test
    void shouldPassWhenMentionSatisfied() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.DINGTALK, orchestratorService, new ChannelPolicy(true, Set.of()));
        InboundEnvelope envelope = envelope(ChannelType.DINGTALK, true, Map.of());

        channel.consume(envelope);
        verify(orchestratorService, times(1)).processInbound(envelope);
    }

    @Test
    void shouldBlockFeishuGroupMessageWithoutBotMention() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.FEISHU, orchestratorService, new ChannelPolicy(false, Set.of()));
        InboundEnvelope envelope = envelope(ChannelType.FEISHU, false, Map.of("chatType", "group"));

        channel.consume(envelope);
        verify(orchestratorService, never()).processInbound(envelope);
    }

    @Test
    void shouldPassFeishuGroupMessageWithBotMention() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.FEISHU, orchestratorService, new ChannelPolicy(false, Set.of()));
        InboundEnvelope envelope = envelope(ChannelType.FEISHU, true, Map.of("chatType", "group"));

        channel.consume(envelope);
        verify(orchestratorService, times(1)).processInbound(envelope);
    }

    @Test
    void shouldBlockDingTalkGroupMessageWithoutBotMention() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.DINGTALK, orchestratorService, new ChannelPolicy(false, Set.of()));
        InboundEnvelope envelope = envelope(ChannelType.DINGTALK, false, Map.of("conversationType", "2"));

        channel.consume(envelope);
        verify(orchestratorService, never()).processInbound(envelope);
    }

    @Test
    void shouldPassDingTalkGroupMessageWithBotMention() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.DINGTALK, orchestratorService, new ChannelPolicy(false, Set.of()));
        InboundEnvelope envelope = envelope(ChannelType.DINGTALK, true, Map.of("conversationType", "2"));

        channel.consume(envelope);
        verify(orchestratorService, times(1)).processInbound(envelope);
    }

    @Test
    void shouldPassPrivateMessageWithoutMention() {
        ChannelOrchestratorService orchestratorService = mock(ChannelOrchestratorService.class);
        AbstractWebhookChannel channel = new TestWebhookChannel(ChannelType.FEISHU, orchestratorService, new ChannelPolicy(false, Set.of()));
        InboundEnvelope envelope = envelope(ChannelType.FEISHU, false, Map.of("chatType", "p2p"));

        channel.consume(envelope);
        verify(orchestratorService, times(1)).processInbound(envelope);
    }

    private InboundEnvelope envelope(ChannelType channel, boolean mentioned, Map<String, String> metadata) {
        return new InboundEnvelope(
                channel,
                "tenant",
                "msg-" + channel.value() + "-" + mentioned,
                "session-1",
                "user-1",
                "hello",
                mentioned,
                "https://example.com",
                Instant.now(),
                metadata
        );
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
