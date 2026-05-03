package ai.nomoclaw.bot.channel.stream;

import ai.nomoclaw.bot.channel.core.ChannelManager;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "agent.channels.dingtalk", name = "enabled", havingValue = "true")
@Slf4j
public class DingTalkStreamConnector implements ChannelStreamConnector {

    private final ChannelManager channelManager;
    private final AgentChannelsProperties properties;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile OpenDingTalkClient streamClient;
    private ExecutorService executor;

    public DingTalkStreamConnector(ChannelManager channelManager, AgentChannelsProperties properties) {
        this.channelManager = channelManager;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "dingtalk-stream";
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        String clientId = trim(properties.getDingtalk().getClientId());
        String clientSecret = trim(properties.getDingtalk().getClientSecret());
        if (clientId.isBlank() || clientSecret.isBlank()) {
            log.warn("[DingTalkStream] missing app credentials, connector disabled");
            started.set(false);
            return;
        }
        streamClient = OpenDingTalkStreamClientBuilder.custom()
                .credential(new AuthClientCredential(clientId, clientSecret))
                .registerCallbackListener(DingTalkStreamTopics.BOT_MESSAGE_TOPIC, (ChatbotMessage message) -> {
                    onMessage(message, clientId);
                    return Map.of("status", "ok");
                })
                .build();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "dingtalk-stream-connector");
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            try {
                streamClient.start();
            } catch (Exception ex) {
                log.error("[DingTalkStream] stream client exited unexpectedly", ex);
            } finally {
                started.set(false);
            }
        });
    }

    @Override
    public void stop() {
        OpenDingTalkClient client = this.streamClient;
        this.streamClient = null;
        if (client != null) {
            try {
                client.stop();
            } catch (Exception ex) {
                log.warn("[DingTalkStream] stop failed", ex);
            }
        }
        ExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }
        started.set(false);
    }

    private void onMessage(ChatbotMessage message, String tenantId) {
        if (message == null) {
            return;
        }
        String text = extractText(message);
        if (text.isBlank()) {
            return;
        }
        String senderId = firstNonBlank(
                trim(message.getSenderId()),
                trim(message.getSenderStaffId())
        );
        String sessionId = firstNonBlank(
                trim(message.getConversationId()),
                senderId
        );
        String replyTarget = trim(message.getSessionWebhook());
        if (sessionId.isBlank()) {
            return;
        }
        String conversationType = trim(message.getConversationType());
        boolean mentioned = !isGroupConversation(conversationType) || Boolean.TRUE.equals(message.getInAtList());
        InboundEnvelope envelope = new InboundEnvelope(
                ChannelType.DINGTALK,
                trim(tenantId),
                trim(message.getMsgId()),
                sessionId,
                senderId,
                text,
                mentioned,
                replyTarget,
                Instant.now(),
                Map.of("conversationType", conversationType)
        );
        channelManager.enqueue(envelope);
    }

    private String extractText(ChatbotMessage message) {
        MessageContent text = message.getText();
        if (text != null) {
            String content = trim(text.getContent());
            if (!content.isBlank()) {
                return content;
            }
            content = trim(text.getText());
            if (!content.isBlank()) {
                return content;
            }
        }
        MessageContent content = message.getContent();
        if (content != null) {
            String body = trim(content.getContent());
            if (!body.isBlank()) {
                return body;
            }
            body = trim(content.getText());
            if (!body.isBlank()) {
                return body;
            }
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean isGroupConversation(String conversationType) {
        return "2".equals(conversationType)
                || "group".equalsIgnoreCase(conversationType)
                || "group_chat".equalsIgnoreCase(conversationType)
                || "chat".equalsIgnoreCase(conversationType);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
