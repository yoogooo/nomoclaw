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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "agent.channels.telegram", name = "enabled", havingValue = "true")
@Slf4j
public class TelegramPollingConnector implements ChannelStreamConnector {

    private final ChannelManager channelManager;
    private final AgentChannelsProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ExecutorService executor;
    private volatile long offset;

    public TelegramPollingConnector(ChannelManager channelManager,
                                    AgentChannelsProperties properties,
                                    HttpClient appHttpClient) {
        this.channelManager = channelManager;
        this.properties = properties;
        this.httpClient = appHttpClient;
    }

    @Override
    public String name() {
        return "telegram-polling";
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (trim(properties.getTelegram().getToken()).isBlank()) {
            log.warn("[TelegramPolling] missing bot token, connector disabled");
            started.set(false);
            return;
        }
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "telegram-polling-connector");
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
                    log.warn("[TelegramPolling] poll failed", ex);
                    sleepBeforeRetry();
                }
            }
        } finally {
            started.set(false);
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void pollOnce() throws Exception {
        String token = trim(properties.getTelegram().getToken());
        String url = "https://api.telegram.org/bot" + token + "/getUpdates?timeout=30&allowed_updates="
                + URLEncoder.encode("[\"message\",\"edited_message\"]", StandardCharsets.UTF_8);
        if (offset > 0) {
            url += "&offset=" + offset;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(40))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            log.warn("[TelegramPolling] getUpdates failed status={} body={}", response.statusCode(), response.body());
            return;
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!root.path("ok").asBoolean(false)) {
            log.warn("[TelegramPolling] getUpdates not ok body={}", response.body());
            return;
        }
        for (JsonNode update : root.path("result")) {
            long updateId = update.path("update_id").asLong(0);
            offset = Math.max(offset, updateId + 1);
            onUpdate(update);
        }
    }

    private void onUpdate(JsonNode update) {
        JsonNode message = update.path("message").isMissingNode() ? update.path("edited_message") : update.path("message");
        if (message.isMissingNode() || message.isNull()) {
            return;
        }
        String text = firstNonBlank(message.path("text").asText(""), message.path("caption").asText(""));
        if (text.isBlank()) {
            return;
        }
        JsonNode chat = message.path("chat");
        JsonNode from = message.path("from");
        String chatId = chat.path("id").asText("");
        String userId = from.path("id").asText("");
        String chatType = chat.path("type").asText("");
        String threadId = message.path("message_thread_id").asText("");
        String sessionKey = threadId.isBlank() ? chatId : chatId + ":" + threadId;
        if (sessionKey.isBlank()) {
            return;
        }
        boolean group = "group".equals(chatType) || "supergroup".equals(chatType) || "channel".equals(chatType);
        boolean mentioned = !group || isMentioned(message, text);
        if (mentioned) {
            text = removeBotMention(text);
            if (text.isBlank()) {
                return;
            }
        }
        InboundEnvelope envelope = new InboundEnvelope(
                ChannelType.TELEGRAM,
                "",
                message.path("message_id").asText(""),
                sessionKey,
                userId,
                text,
                mentioned,
                "telegram:chat_id:" + chatId,
                Instant.now(),
                Map.of("chatType", chatType, "messageThreadId", threadId)
        );
        channelManager.enqueue(envelope);
    }

    private boolean isMentioned(JsonNode message, String text) {
        String username = trim(properties.getTelegram().getBotUsername());
        if (username.startsWith("@")) {
            username = username.substring(1).trim();
        }
        if (username.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("@" + username.toLowerCase(Locale.ROOT))
                || hasBotCommand(message.path("entities"))
                || hasBotCommand(message.path("caption_entities"));
    }

    private boolean hasBotCommand(JsonNode entities) {
        if (!entities.isArray()) {
            return false;
        }
        for (JsonNode entity : entities) {
            if ("bot_command".equals(entity.path("type").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private String removeBotMention(String text) {
        String username = trim(properties.getTelegram().getBotUsername());
        if (username.startsWith("@")) {
            username = username.substring(1).trim();
        }
        if (username.isBlank()) {
            return text;
        }
        return text.replaceAll("(?i)@" + Pattern.quote(username) + "\\b", "").trim();
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
}
