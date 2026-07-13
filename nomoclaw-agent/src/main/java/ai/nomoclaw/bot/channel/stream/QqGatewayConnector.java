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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
@ConditionalOnProperty(prefix = "agent.channels.qq", name = "enabled", havingValue = "true")
@Slf4j
public class QqGatewayConnector implements ChannelStreamConnector {

    private static final int INTENTS = (1 << 30) | (1 << 12) | (1 << 25) | (1 << 1);
    private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";

    private final ChannelManager channelManager;
    private final AgentChannelsProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private volatile WebSocket webSocket;
    private volatile Integer lastSequence;
    private volatile String accessToken = "";

    public QqGatewayConnector(ChannelManager channelManager,
                              AgentChannelsProperties properties,
                              HttpClient appHttpClient) {
        this.channelManager = channelManager;
        this.properties = properties;
        this.httpClient = appHttpClient;
    }

    @Override
    public String name() {
        return "qq-gateway";
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (trim(properties.getQq().getAppId()).isBlank() || trim(properties.getQq().getClientSecret()).isBlank()) {
            log.warn("[QqGateway] missing appId/clientSecret, connector disabled");
            started.set(false);
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "qq-gateway-heartbeat");
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
        try {
            accessToken = appAccessToken();
            String gatewayUrl = gatewayUrl(accessToken);
            httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(gatewayUrl), new GatewayListener())
                    .whenComplete((socket, error) -> {
                        if (error != null) {
                            log.warn("[QqGateway] connect failed", error);
                            scheduleReconnect();
                            return;
                        }
                        webSocket = socket;
                    });
        } catch (Exception ex) {
            log.warn("[QqGateway] connect setup failed", ex);
            scheduleReconnect();
        }
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
            if (op == 0) {
                onDispatch(root.path("t").asText(""), root.path("d"));
            }
        } catch (Exception ex) {
            log.warn("[QqGateway] payload handling failed", ex);
        }
    }

    private void onDispatch(String eventType, JsonNode message) {
        if (!eventType.endsWith("MESSAGE_CREATE")) {
            return;
        }
        String text = removeBotMention(trim(message.path("content").asText("")));
        if (text.isBlank()) {
            return;
        }
        String messageId = trim(message.path("id").asText(""));
        String senderId = firstNonBlank(message.path("author").path("user_openid").asText(""),
                message.path("author").path("member_openid").asText(""),
                message.path("author").path("id").asText(""));
        String guildId = trim(message.path("guild_id").asText(""));
        String channelId = trim(message.path("channel_id").asText(""));
        String groupId = trim(message.path("group_openid").asText(""));
        String sessionKey = firstNonBlank(groupId, channelId, senderId);
        String replyTarget = replyTarget(eventType, channelId, groupId, guildId, senderId);
        if (sessionKey.isBlank() || replyTarget.isBlank()) {
            return;
        }
        InboundEnvelope envelope = new InboundEnvelope(
                ChannelType.QQ,
                guildId,
                messageId,
                sessionKey,
                senderId,
                text,
                true,
                replyTarget,
                Instant.now(),
                Map.of("guildId", guildId, "groupId", groupId, "eventType", eventType)
        );
        channelManager.enqueue(envelope);
    }

    private String replyTarget(String eventType, String channelId, String groupId, String guildId, String senderId) {
        return switch (eventType) {
            case "C2C_MESSAGE_CREATE" -> "qq:c2c_openid:" + senderId;
            case "GROUP_AT_MESSAGE_CREATE" -> "qq:group_openid:" + groupId;
            case "DIRECT_MESSAGE_CREATE" -> "qq:dm_guild_id:" + guildId;
            default -> "qq:channel_id:" + channelId;
        };
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
        String sequence = lastSequence == null ? "null" : lastSequence.toString();
        current.sendText("{\"op\":1,\"d\":" + sequence + "}", true);
    }

    private void identify() throws Exception {
        WebSocket current = webSocket;
        if (current == null) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "op", 2,
                "d", Map.of(
                        "token", "QQBot " + accessToken,
                        "intents", INTENTS,
                        "properties", Map.of("os", "java", "browser", "nomoclaw", "device", "nomoclaw")
                )
        );
        current.sendText(objectMapper.writeValueAsString(payload), true);
    }

    private String appAccessToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "appId", trim(properties.getQq().getAppId()),
                "clientSecret", trim(properties.getQq().getClientSecret())
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String token = objectMapper.readTree(response.body()).path("access_token").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("fetch qq app access token failed: " + response.body());
        }
        return token;
    }

    private String gatewayUrl(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase() + "/gateway"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "QQBot " + token)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String url = objectMapper.readTree(response.body()).path("url").asText("");
        if (url.isBlank()) {
            throw new IllegalStateException("fetch qq gateway failed: " + response.body());
        }
        return url;
    }

    private String removeBotMention(String text) {
        String botUserId = trim(properties.getQq().getBotUserId());
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

    private String apiBase() {
        return properties.getQq().isSandbox() ? "https://sandbox.api.sgroup.qq.com" : "https://api.sgroup.qq.com";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trim(value);
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return "";
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private class GatewayListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            QqGatewayConnector.this.webSocket = webSocket;
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
            log.info("[QqGateway] closed status={} reason={}", statusCode, reason);
            scheduleReconnect();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("[QqGateway] websocket error", error);
            scheduleReconnect();
        }
    }
}
