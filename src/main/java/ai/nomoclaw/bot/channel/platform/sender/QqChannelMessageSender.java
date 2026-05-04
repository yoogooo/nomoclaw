package ai.nomoclaw.bot.channel.platform.sender;

import ai.nomoclaw.bot.channel.config.ChannelBotCredentialResolver;
import ai.nomoclaw.bot.channel.model.ChannelAddress;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.ChannelMessageSender;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "agent.channels.qq", name = "enabled", havingValue = "true")
@Slf4j
public class QqChannelMessageSender implements ChannelMessageSender {

    private static final String TOKEN_URL = "https://bots.qq.com/app/getAppAccessToken";

    private final HttpClient httpClient;
    private final ChannelBotCredentialResolver credentialResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger msgSeq = new AtomicInteger(1);

    private volatile String cachedToken = "";
    private volatile Instant tokenExpireAt = Instant.EPOCH;

    public QqChannelMessageSender(HttpClient appHttpClient, ChannelBotCredentialResolver credentialResolver) {
        this.httpClient = appHttpClient;
        this.credentialResolver = credentialResolver;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.QQ;
    }

    @Override
    public void send(OutboundMessage message) {
        String requestedBotId = message.metadata().getOrDefault("botId", "");
        ChannelBotCredentialResolver.QqBotCredential credential = credentialResolver.resolveQq(requestedBotId);
        if (credential == null || credential.appId().isBlank() || credential.clientSecret().isBlank()) {
            log.warn("[QqSender] missing appId/clientSecret");
            return;
        }
        QqTarget target = QqTarget.parse(message.address());
        if (target.value().isBlank()) {
            log.warn("[QqSender] empty target");
            return;
        }
        try {
            String accessToken = appAccessToken(credential);
            String body = objectMapper.writeValueAsString(buildBody(target, message.text()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase(credential) + target.path()))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "QQBot " + accessToken)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("[QqSender] send failed kind={} status={} body={}", target.kind(), response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("[QqSender] send error kind={} target={}", target.kind(), target.value(), ex);
        }
    }

    private Map<String, Object> buildBody(QqTarget target, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", sanitize(MarkdownToReadableTextFormatter.format(text)));
        if (target.needsMsgSeq()) {
            body.put("msg_type", 0);
            body.put("msg_seq", msgSeq.getAndUpdate(current -> current >= 999_999 ? 1 : current + 1));
        }
        return body;
    }

    private synchronized String appAccessToken(ChannelBotCredentialResolver.QqBotCredential credential) throws Exception {
        if (!cachedToken.isBlank() && Instant.now().isBefore(tokenExpireAt.minusSeconds(60))) {
            return cachedToken;
        }
        String body = objectMapper.writeValueAsString(Map.of(
                "appId", credential.appId(),
                "clientSecret", credential.clientSecret()
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode root = objectMapper.readTree(response.body());
        String token = root.path("access_token").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("fetch qq app access token failed: " + response.body());
        }
        cachedToken = token;
        tokenExpireAt = Instant.now().plusSeconds(Math.max(120, root.path("expires_in").asLong(7200)));
        return cachedToken;
    }

    private String apiBase(ChannelBotCredentialResolver.QqBotCredential credential) {
        return credential.sandbox() ? "https://sandbox.api.sgroup.qq.com" : "https://api.sgroup.qq.com";
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replaceAll("https?://\\S+|www\\.\\S+", "[链接已省略]");
    }

    private record QqTarget(String kind, String value) {
        private static QqTarget parse(ChannelAddress address) {
            String target = address == null ? "" : address.target();
            if (target == null || target.isBlank()) {
                return new QqTarget("channel_id", "");
            }
            String[] parts = target.split(":", 3);
            if (parts.length == 3 && "qq".equalsIgnoreCase(parts[0])) {
                return new QqTarget(parts[1], parts[2]);
            }
            return new QqTarget("channel_id", target);
        }

        private String path() {
            return switch (kind) {
                case "c2c_openid", "user_openid" -> "/v2/users/" + value + "/messages";
                case "group_openid" -> "/v2/groups/" + value + "/messages";
                case "dm_guild_id" -> "/dms/" + value + "/messages";
                default -> "/channels/" + value + "/messages";
            };
        }

        private boolean needsMsgSeq() {
            return "c2c_openid".equals(kind) || "user_openid".equals(kind) || "group_openid".equals(kind);
        }
    }
}
