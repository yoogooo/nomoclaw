package ai.nomoclaw.bot.channel.platform.sender;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.ChannelMessageSender;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReactionReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReactionReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageReactionResp;
import com.lark.oapi.service.im.v1.model.Emoji;
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
public class FeishuChannelMessageSender implements ChannelMessageSender {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String appId;
    private final String appSecret;
    private final boolean processingAckReactionEnabled;
    private final String processingAckReactionType;
    private final Client larkClient;
    private volatile String tokenValue;
    private volatile Instant tokenExpireAt = Instant.EPOCH;

    public FeishuChannelMessageSender(AgentChannelsProperties properties) {
        this.appId = properties.getFeishu().getAppId();
        this.appSecret = properties.getFeishu().getAppSecret();
        this.processingAckReactionEnabled = properties.getFeishu().isProcessingAckReactionEnabled();
        this.processingAckReactionType = properties.getFeishu().getProcessingAckReactionType();
        this.larkClient = Client.newBuilder(this.appId, this.appSecret).build();
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.FEISHU;
    }

    @Override
    public void send(OutboundMessage message) {
        if (trySendProcessingAckReaction(message)) {
            return;
        }
        String formattedText = MarkdownToReadableTextFormatter.format(message.text());
        String kind = message.address().kind();
        String target = message.address().target();
        if (target == null || target.isBlank()) {
            log.warn("[FeishuSender] empty target");
            return;
        }
        String receiveType = switch (kind) {
            case "chat_id" -> "chat_id";
            case "open_id" -> "open_id";
            case "user_id" -> "user_id";
            default -> null;
        };
        if (receiveType == null) {
            postWebhook(target, formattedText);
            return;
        }
        try {
            String token = tenantAccessToken();
            String content = objectMapper.writeValueAsString(Map.of("text", formattedText));
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
                log.warn("[FeishuSender] send failed status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("[FeishuSender] send error target={}", target, ex);
        }
    }

    private boolean trySendProcessingAckReaction(OutboundMessage message) {
        if (!processingAckReactionEnabled) {
            return false;
        }
        String phase = trim(message.metadata().get("phase"));
        if (!"processing_ack".equals(phase)) {
            return false;
        }
        String externalMessageId = trim(message.metadata().get("externalMessageId"));
        if (externalMessageId.isBlank()) {
            return false;
        }
        try {
            CreateMessageReactionReq req = CreateMessageReactionReq.newBuilder()
                    .messageId(externalMessageId)
                    .createMessageReactionReqBody(CreateMessageReactionReqBody.newBuilder()
                            .reactionType(Emoji.newBuilder().emojiType(processingAckReactionType).build())
                            .build())
                    .build();
            CreateMessageReactionResp resp = larkClient.im().messageReaction().create(req);
            if (resp.success()) {
                return true;
            }
            log.warn("[FeishuSender] reaction failed code={} msg={} messageId={}",
                    resp.getCode(), resp.getMsg(), externalMessageId);
            return false;
        } catch (Exception ex) {
            log.warn("[FeishuSender] reaction error messageId={}", externalMessageId, ex);
            return false;
        }
    }

    private void postWebhook(String target, String text) {
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            log.warn("[FeishuSender] unsupported target={}", target);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "msg_type", "text",
                    "content", Map.of("text", text)
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("[FeishuSender] webhook send failed status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("[FeishuSender] webhook send error target={}", target, ex);
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

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
