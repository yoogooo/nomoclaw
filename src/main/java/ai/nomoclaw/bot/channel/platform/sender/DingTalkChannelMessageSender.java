package ai.nomoclaw.bot.channel.platform.sender;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.ChannelMessageSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "agent.channels.dingtalk", name = "enabled", havingValue = "true")
@Slf4j
public class DingTalkChannelMessageSender implements ChannelMessageSender {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public DingTalkChannelMessageSender(HttpClient appHttpClient) {
        this.httpClient = appHttpClient;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.DINGTALK;
    }

    @Override
    public void send(OutboundMessage message) {
        String title = DingTalkMarkdownFormatter.extractTitle(message.text());
        String markdownText = DingTalkMarkdownFormatter.formatMarkdown(message.text());
        String plainText = DingTalkMarkdownFormatter.formatPlainText(message.text());
        String target = message.address().target();
        if (target == null || target.isBlank()) {
            log.warn("[DingTalkSender] empty target");
            return;
        }
        try {
            BotReplier.fromWebhook(target).replyMarkdown(title, markdownText);
        } catch (Exception ex) {
            log.warn("[DingTalkSender] markdown send failed, fallback webhook target={}", target, ex);
            if (!postMarkdownWebhook(target, title, markdownText)) {
                postTextWebhook(target, plainText);
            }
        }
    }

    private boolean postMarkdownWebhook(String target, String title, String text) {
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            log.warn("[DingTalkSender] unsupported target={}", target);
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of(
                            "title", title,
                            "text", text
                    )
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("[DingTalkSender] markdown webhook send failed status={} body={}", response.statusCode(), response.body());
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.error("[DingTalkSender] markdown webhook send error target={}", target, ex);
            return false;
        }
    }

    private void postTextWebhook(String target, String text) {
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            log.warn("[DingTalkSender] unsupported target={}", target);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", text)
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("[DingTalkSender] text webhook send failed status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("[DingTalkSender] text webhook send error target={}", target, ex);
        }
    }
}
