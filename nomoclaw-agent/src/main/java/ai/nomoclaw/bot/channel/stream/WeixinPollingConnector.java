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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "agent.channels.weixin", name = "enabled", havingValue = "true")
@Slf4j
public class WeixinPollingConnector implements ChannelStreamConnector {

    private static final String CHANNEL_VERSION = "2.0.1";

    private final ChannelManager channelManager;
    private final AgentChannelsProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ExecutorService executor;
    private volatile String cursor = "";

    public WeixinPollingConnector(ChannelManager channelManager,
                                  AgentChannelsProperties properties,
                                  HttpClient appHttpClient) {
        this.channelManager = channelManager;
        this.properties = properties;
        this.httpClient = appHttpClient;
    }

    @Override
    public String name() {
        return "weixin-polling";
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (trim(properties.getWeixin().getBotToken()).isBlank()) {
            log.warn("[WeixinPolling] missing iLink bot token, connector disabled");
            started.set(false);
            return;
        }
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "weixin-polling-connector");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::pollLoop);
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

    private void pollLoop() {
        try {
            while (started.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    pollOnce();
                } catch (Exception ex) {
                    log.warn("[WeixinPolling] poll failed", ex);
                    sleepBeforeRetry();
                }
            }
        } finally {
            started.set(false);
        }
    }

    private void pollOnce() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "get_updates_buf", cursor,
                "base_info", Map.of("channel_version", CHANNEL_VERSION)
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/ilink/bot/getupdates"))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + trim(properties.getWeixin().getBotToken()))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            log.warn("[WeixinPolling] getupdates failed status={} body={}", response.statusCode(), response.body());
            return;
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (root.path("ret").asInt(0) != 0) {
            log.warn("[WeixinPolling] getupdates returned body={}", response.body());
            return;
        }
        cursor = root.path("get_updates_buf").asText(cursor);
        for (JsonNode msg : root.path("msgs")) {
            onMessage(msg);
        }
    }

    private void onMessage(JsonNode msg) {
        String toUserId = trim(msg.path("from_user_id").asText(""));
        String contextToken = trim(msg.path("context_token").asText(""));
        String text = extractText(msg.path("item_list"));
        if (toUserId.isBlank() || contextToken.isBlank() || text.isBlank()) {
            return;
        }
        InboundEnvelope envelope = new InboundEnvelope(
                ChannelType.WEIXIN,
                "",
                trim(msg.path("client_id").asText("")),
                toUserId,
                toUserId,
                text,
                true,
                "weixin:user_id:" + toUserId + "|" + contextToken,
                Instant.now(),
                Map.of("contextToken", contextToken)
        );
        channelManager.enqueue(envelope);
    }

    private String extractText(JsonNode items) {
        StringBuilder builder = new StringBuilder();
        if (!items.isArray()) {
            return "";
        }
        for (JsonNode item : items) {
            String text = trim(item.path("text_item").path("text").asText(""));
            if (!text.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(text);
            }
        }
        return builder.toString();
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String baseUrl() {
        String value = trim(properties.getWeixin().getBaseUrl());
        return (value.isBlank() ? "https://ilinkai.weixin.qq.com" : value).replaceAll("/+$", "");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
