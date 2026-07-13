package ai.nomoclaw.bot.channel.stream;

import ai.nomoclaw.bot.channel.core.ChannelManager;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.channel.config.ChannelBotCredentialResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.MentionEvent;
import com.lark.oapi.service.im.v1.model.P2MessageReadV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReactionCreatedV1;
import com.lark.oapi.service.im.v1.model.P2MessageReactionDeletedV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.service.im.v1.model.UserId;
import com.lark.oapi.ws.Client;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "agent.channels.feishu", name = "enabled", havingValue = "true")
@Slf4j
public class FeishuStreamConnector implements ChannelStreamConnector {

    private final ChannelManager channelManager;
    private final AgentChannelsProperties properties;
    private final ChannelBotCredentialResolver botCredentialResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ExecutorService executor;
    private volatile Client wsClient;

    public FeishuStreamConnector(ChannelManager channelManager,
                                 AgentChannelsProperties properties,
                                 ChannelBotCredentialResolver botCredentialResolver) {
        this.channelManager = channelManager;
        this.properties = properties;
        this.botCredentialResolver = botCredentialResolver;
    }

    @Override
    public String name() {
        return "feishu-stream";
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        String appId = trim(properties.getFeishu().getAppId());
        String appSecret = trim(properties.getFeishu().getAppSecret());
        if (appId.isBlank() || appSecret.isBlank()) {
            log.warn("[FeishuStream] missing app credentials, connector disabled");
            started.set(false);
            return;
        }
        EventDispatcher dispatcher = EventDispatcher
                .newBuilder(
                        "",
                        ""
                )
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) {
                        onMessage(event);
                    }
                })
                .onP2MessageReadV1(new ImService.P2MessageReadV1Handler() {
                    @Override
                    public void handle(P2MessageReadV1 event) {
                        // ignore read-receipt events in V1
                    }
                })
                .onP2MessageReactionCreatedV1(new ImService.P2MessageReactionCreatedV1Handler() {
                    @Override
                    public void handle(P2MessageReactionCreatedV1 event) {
                        // ignore reaction events in V1
                    }
                })
                .onP2MessageReactionDeletedV1(new ImService.P2MessageReactionDeletedV1Handler() {
                    @Override
                    public void handle(P2MessageReactionDeletedV1 event) {
                        // ignore reaction events in V1
                    }
                })
                .build();
        wsClient = new Client.Builder(appId, appSecret)
                .eventHandler(dispatcher)
                .build();
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "feishu-stream-connector");
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            try {
                wsClient.start();
            } catch (Exception ex) {
                log.error("[FeishuStream] ws client exited unexpectedly", ex);
            } finally {
                started.set(false);
            }
        });
    }

    @Override
    public void stop() {
        ExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }
        started.set(false);
    }

    private void onMessage(P2MessageReceiveV1 event) {
        if (event == null) {
            return;
        }
        P2MessageReceiveV1Data body = event.getEvent();
        if (body == null) {
            return;
        }
        EventMessage message = body.getMessage();
        EventSender sender = body.getSender();
        if (message == null || sender == null) {
            return;
        }
        if (!"text".equalsIgnoreCase(trim(message.getMessageType()))) {
            return;
        }
        if ("app".equalsIgnoreCase(trim(sender.getSenderType()))) {
            return;
        }

        UserId senderId = sender.getSenderId();
        String openId = senderId == null ? "" : trim(senderId.getOpenId());
        String userId = senderId == null ? "" : trim(senderId.getUserId());
        String senderKey = !openId.isBlank() ? openId : userId;
        String chatId = trim(message.getChatId());
        String sessionId = !chatId.isBlank() ? chatId : senderKey;
        String text = extractText(message.getContent());
        if (sessionId.isBlank() || text.isBlank()) {
            return;
        }

        String chatType = trim(message.getChatType());
        boolean p2p = "p2p".equalsIgnoreCase(chatType);
        boolean mentioned = p2p || isBotMentioned(message.getMentions());
        String replyTarget = !chatId.isBlank()
                ? "feishu:chat_id:" + chatId
                : "feishu:open_id:" + senderKey;
        InboundEnvelope envelope = new InboundEnvelope(
                ChannelType.FEISHU,
                trim(sender.getTenantKey()),
                trim(message.getMessageId()),
                sessionId,
                senderKey,
                text,
                mentioned,
                replyTarget,
                Instant.now(),
                Map.of("chatType", chatType)
        );
        channelManager.enqueue(envelope);
    }

    private String extractText(String rawContent) {
        String content = trim(rawContent);
        if (content.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            String text = root.path("text").asText("");
            return trim(text);
        } catch (Exception ex) {
            return content;
        }
    }

    private boolean isBotMentioned(MentionEvent[] mentions) {
        if (mentions == null || mentions.length == 0) {
            return false;
        }
        String botOpenId = defaultBotOpenId();
        if (botOpenId.isBlank()) {
            return true;
        }
        for (MentionEvent mention : mentions) {
            UserId id = mention == null ? null : mention.getId();
            String openId = id == null ? "" : trim(id.getOpenId());
            if (botOpenId.equals(openId)) {
                return true;
            }
        }
        return false;
    }

    private String defaultBotOpenId() {
        ChannelBotCredentialResolver.FeishuBotCredential credential = botCredentialResolver.resolveFeishu("");
        if (credential == null) {
            return "";
        }
        String target = trim(credential.defaultTarget());
        String prefix = "feishu:open_id:";
        if (!target.startsWith(prefix)) {
            return "";
        }
        return target.substring(prefix.length()).trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
