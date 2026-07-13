package ai.nomoclaw.bot.channel.stream;

import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.channel.core.ChannelManager;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "agent.channels.discord", name = "enabled", havingValue = "true")
@Slf4j
public class DiscordGatewayConnector implements ChannelStreamConnector {

    private static final int INTENTS = 1 | 512 | 4096 | 32768;

    private final ChannelManager channelManager;
    private final AgentChannelsProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private volatile WebSocket webSocket;
    private volatile Integer lastSequence;

    public DiscordGatewayConnector(ChannelManager channelManager,
                                   AgentChannelsProperties properties,
                                   HttpClient appHttpClient) {
        this.channelManager = channelManager;
        this.properties = properties;
        this.httpClient = appHttpClient;
    }

    @Override
    public String name() {
        return "discord-gateway";
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (trim(properties.getDiscord().getToken()).isBlank()) {
            log.warn("[DiscordGateway] missing bot token, connector disabled");
            started.set(false);
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "discord-gateway-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        connect();
    }

    @Override
    public void stop() {
        started.set(false);
        ScheduledFuture<?> currentHeartbeat = heartbeatTask;
        heartbeatTask = null;
        if (currentHeartbeat != null) {
            currentHeartbeat.cancel(true);
        }
        ScheduledExecutorService currentScheduler = scheduler;
        scheduler = null;
        if (currentScheduler != null) {
            currentScheduler.shutdownNow();
        }
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            current.abort();
        }
    }

    private void connect() {
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("wss://gateway.discord.gg/?v=10&encoding=json"), new GatewayListener())
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        log.warn("[DiscordGateway] connect failed", error);
                        scheduleReconnect();
                        return;
                    }
                    webSocket = socket;
                });
    }

    private void onPayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.path("s").isNull()) {
                lastSequence = root.path("s").asInt();
            }
            int op = root.path("op").asInt(-1);
            if (op == 10) {
                startHeartbeat(root.path("d").path("heartbeat_interval").asLong(45000));
                identify();
                return;
            }
            if (op == 0 && "MESSAGE_CREATE".equals(root.path("t").asText(""))) {
                onMessage(root.path("d"));
            }
        } catch (Exception ex) {
            log.warn("[DiscordGateway] payload handling failed", ex);
        }
    }

    private void startHeartbeat(long intervalMillis) {
        ScheduledFuture<?> current = heartbeatTask;
        if (current != null) {
            current.cancel(true);
        }
        ScheduledExecutorService currentScheduler = scheduler;
        if (currentScheduler == null) {
            return;
        }
        heartbeatTask = currentScheduler.scheduleAtFixedRate(this::heartbeat, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void heartbeat() {
        WebSocket current = webSocket;
        if (current == null) {
            return;
        }
        try {
            String sequence = lastSequence == null ? "null" : lastSequence.toString();
            current.sendText("{\"op\":1,\"d\":" + sequence + "}", true);
        } catch (Exception ex) {
            log.warn("[DiscordGateway] heartbeat failed", ex);
        }
    }

    private void identify() throws Exception {
        WebSocket current = webSocket;
        if (current == null) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "op", 2,
                "d", Map.of(
                        "token", trim(properties.getDiscord().getToken()),
                        "intents", INTENTS,
                        "properties", Map.of("os", "java", "browser", "nomoclaw", "device", "nomoclaw")
                )
        );
        current.sendText(objectMapper.writeValueAsString(payload), true);
    }

    private void onMessage(JsonNode message) {
        JsonNode author = message.path("author");
        if (author.path("id").asText("").equals(trim(properties.getDiscord().getBotUserId()))) {
            return;
        }
        if (author.path("bot").asBoolean(false) && !properties.getDiscord().isAcceptBotMessages()) {
            return;
        }
        String text = trim(message.path("content").asText(""));
        if (text.isBlank()) {
            return;
        }
        String channelId = trim(message.path("channel_id").asText(""));
        String guildId = trim(message.path("guild_id").asText(""));
        if (channelId.isBlank()) {
            return;
        }
        boolean group = !guildId.isBlank();
        boolean mentioned = !group || isMentioned(message, text);
        if (mentioned) {
            text = removeBotMention(text);
            if (text.isBlank()) {
                return;
            }
        }
        InboundEnvelope envelope = new InboundEnvelope(
                ChannelType.DISCORD,
                guildId,
                trim(message.path("id").asText("")),
                channelId,
                trim(author.path("id").asText("")),
                text,
                mentioned,
                "discord:channel_id:" + channelId,
                Instant.now(),
                Map.of("guildId", guildId)
        );
        channelManager.enqueue(envelope);
    }

    private boolean isMentioned(JsonNode message, String text) {
        String botUserId = trim(properties.getDiscord().getBotUserId());
        if (botUserId.isBlank()) {
            return false;
        }
        if (text.contains("<@" + botUserId + ">") || text.contains("<@!" + botUserId + ">")) {
            return true;
        }
        for (JsonNode mention : message.path("mentions")) {
            if (botUserId.equals(mention.path("id").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private String removeBotMention(String text) {
        String botUserId = trim(properties.getDiscord().getBotUserId());
        if (botUserId.isBlank()) {
            return text;
        }
        return text.replaceAll("<@!?" + Pattern.quote(botUserId) + ">", "").trim();
    }

    private void scheduleReconnect() {
        ScheduledExecutorService currentScheduler = scheduler;
        if (!started.get() || currentScheduler == null) {
            return;
        }
        currentScheduler.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private class GatewayListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            DiscordGatewayConnector.this.webSocket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                onPayload(payload);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("[DiscordGateway] closed status={} reason={}", statusCode, reason);
            scheduleReconnect();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("[DiscordGateway] websocket error", error);
            scheduleReconnect();
        }
    }
}
