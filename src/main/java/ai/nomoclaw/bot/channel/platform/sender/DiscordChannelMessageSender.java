package ai.nomoclaw.bot.channel.platform.sender;

import ai.nomoclaw.bot.channel.config.ChannelBotCredentialResolver;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.ChannelMessageSender;
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
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "agent.channels.discord", name = "enabled", havingValue = "true")
@Slf4j
public class DiscordChannelMessageSender implements ChannelMessageSender {

    private static final int MAX_TEXT_LENGTH = 2000;

    private final HttpClient httpClient;
    private final ChannelBotCredentialResolver credentialResolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DiscordChannelMessageSender(HttpClient appHttpClient, ChannelBotCredentialResolver credentialResolver) {
        this.httpClient = appHttpClient;
        this.credentialResolver = credentialResolver;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.DISCORD;
    }

    @Override
    public void send(OutboundMessage message) {
        String requestedBotId = message.metadata().getOrDefault("botId", "");
        ChannelBotCredentialResolver.DiscordBotCredential credential = credentialResolver.resolveDiscord(requestedBotId);
        if (credential == null || credential.token().isBlank()) {
            log.warn("[DiscordSender] missing bot token");
            return;
        }
        String channelId = message.address().target();
        if (channelId == null || channelId.isBlank()) {
            log.warn("[DiscordSender] empty channel_id");
            return;
        }
        String text = MarkdownToReadableTextFormatter.format(message.text());
        for (int start = 0; start < text.length(); start += MAX_TEXT_LENGTH) {
            String chunk = text.substring(start, Math.min(start + MAX_TEXT_LENGTH, text.length()));
            sendChunk(credential.token(), channelId, chunk);
        }
    }

    private void sendChunk(String token, String channelId, String text) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("content", text));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/channels/" + channelId + "/messages"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bot " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("[DiscordSender] send failed status={} body={}", response.statusCode(), response.body());
            }
        } catch (Exception ex) {
            log.error("[DiscordSender] send error channelId={}", channelId, ex);
        }
    }
}
