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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "agent.channels.weixin", name = "enabled", havingValue = "true")
@Slf4j
public class WeixinChannelMessageSender implements ChannelMessageSender {

    private static final String DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com";
    private static final String CHANNEL_VERSION = "2.0.1";

    private final HttpClient httpClient;
    private final ChannelBotCredentialResolver credentialResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeixinChannelMessageSender(HttpClient appHttpClient, ChannelBotCredentialResolver credentialResolver) {
        this.httpClient = appHttpClient;
        this.credentialResolver = credentialResolver;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.WEIXIN;
    }

    @Override
    public void send(OutboundMessage message) {
        String requestedBotId = message.metadata().getOrDefault("botId", "");
        ChannelBotCredentialResolver.WeixinBotCredential credential = credentialResolver.resolveWeixin(requestedBotId);
        String botToken = resolveBotToken(credential);
        if (credential == null || botToken.isBlank()) {
            log.warn("[WeixinSender] missing iLink botToken");
            return;
        }
        WeixinTarget target = WeixinTarget.parse(message.address(), message.metadata().getOrDefault("contextToken", ""));
        if (target.toUserId().isBlank() || target.contextToken().isBlank()) {
            log.warn("[WeixinSender] missing toUserId/contextToken");
            return;
        }
        try {
            Map<String, Object> msg = Map.of(
                    "from_user_id", "",
                    "to_user_id", target.toUserId(),
                    "client_id", UUID.randomUUID().toString(),
                    "message_type", 2,
                    "message_state", 2,
                    "context_token", target.contextToken(),
                    "item_list", List.of(Map.of(
                            "type", 1,
                            "text_item", Map.of("text", MarkdownToReadableTextFormatter.format(message.text()))
                    ))
            );
            String body = objectMapper.writeValueAsString(Map.of(
                    "msg", msg,
                    "base_info", Map.of("channel_version", CHANNEL_VERSION)
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl(credential) + "/ilink/bot/sendmessage"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + botToken)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = objectMapper.readTree(response.body());
            if (response.statusCode() >= 400 || root.path("ret").asInt(0) != 0) {
                log.warn("[WeixinSender] send failed status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("[WeixinSender] send error toUserId={}", target.toUserId(), ex);
        }
    }

    private String resolveBotToken(ChannelBotCredentialResolver.WeixinBotCredential credential) {
        if (credential == null) {
            return "";
        }
        return credential.botToken();
    }

    private String baseUrl(ChannelBotCredentialResolver.WeixinBotCredential credential) {
        String value = credential.baseUrl();
        return value == null || value.isBlank() ? DEFAULT_BASE_URL : value.replaceAll("/+$", "");
    }

    private record WeixinTarget(String toUserId, String contextToken) {
        private static WeixinTarget parse(ChannelAddress address, String metadataContextToken) {
            String target = address == null ? "" : address.target();
            if (target == null || target.isBlank()) {
                return new WeixinTarget("", "");
            }
            String[] targetAndContext = target.split("\\|", 2);
            String toUserId = targetAndContext[0];
            String contextToken = targetAndContext.length > 1 ? targetAndContext[1] : metadataContextToken;
            if (toUserId.startsWith("weixin:user_id:")) {
                toUserId = toUserId.substring("weixin:user_id:".length());
            }
            return new WeixinTarget(toUserId, contextToken == null ? "" : contextToken);
        }
    }
}
