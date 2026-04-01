package ai.nomoclaw.bot.channel.platform;

import ai.nomoclaw.bot.channel.core.ChannelOrchestratorService;
import ai.nomoclaw.bot.channel.model.ChannelPolicy;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.Channel;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
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

@Component
@ConditionalOnProperty(prefix = "agent.channels.feishu", name = "enabled", havingValue = "true")
@Slf4j
public class FeishuChannel extends AbstractWebhookChannel implements Channel {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String appId;
    private final String appSecret;
    private volatile String tokenValue;
    private volatile Instant tokenExpireAt = Instant.EPOCH;

    public FeishuChannel(ChannelOrchestratorService orchestratorService, AgentChannelsProperties properties) {
        super(
                ChannelType.FEISHU,
                orchestratorService,
                new ChannelPolicy(properties.getFeishu().isRequireMention(), properties.getFeishu().allowSet())
        );
        this.appId = properties.getFeishu().getAppId();
        this.appSecret = properties.getFeishu().getAppSecret();
    }

    @Override
    protected String buildPayload(String text) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "msg_type", "text",
                    "content", Map.of("text", text)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void send(OutboundMessage message) {
        String kind = message.address().kind();
        String target = message.address().target();
        if (target == null || target.isBlank()) {
            log.warn("[FeishuChannel] empty target");
            return;
        }
        String receiveType = switch (kind) {
            case "chat_id" -> "chat_id";
            case "open_id" -> "open_id";
            case "user_id" -> "user_id";
            default -> null;
        };
        if (receiveType == null) {
            super.send(message);
            return;
        }
        try {
            String token = tenantAccessToken();
            String content = objectMapper.writeValueAsString(Map.of("text", message.text()));
            String body = objectMapper.writeValueAsString(Map.of(
                    "receive_id", target,
                    "msg_type", "text",
                    "content", content
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveType))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("[FeishuChannel] send failed status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("[FeishuChannel] send error target={}", target, ex);
        }
    }

    private synchronized String tenantAccessToken() throws Exception {
        if (tokenValue != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(60))) {
            return tokenValue;
        }
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "app_id", appId == null ? "" : appId,
                "app_secret", appSecret == null ? "" : appSecret
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode jsonNode = objectMapper.readTree(response.body());
        int code = jsonNode.path("code").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("fetch token failed: " + response.body());
        }
        tokenValue = jsonNode.path("tenant_access_token").asText("");
        int expire = jsonNode.path("expire").asInt(7200);
        tokenExpireAt = Instant.now().plusSeconds(Math.max(120, expire));
        return tokenValue;
    }
}
