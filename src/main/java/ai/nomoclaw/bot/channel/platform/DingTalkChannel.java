package ai.nomoclaw.bot.channel.platform;

import ai.nomoclaw.bot.channel.core.ChannelOrchestratorService;
import ai.nomoclaw.bot.channel.model.ChannelPolicy;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.platform.sender.DingTalkMarkdownFormatter;
import ai.nomoclaw.bot.channel.spi.Channel;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "agent.channels.dingtalk", name = "enabled", havingValue = "true")
@Slf4j
public class DingTalkChannel extends AbstractWebhookChannel implements Channel {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DingTalkChannel(ChannelOrchestratorService orchestratorService,
                           AgentChannelsProperties properties,
                           HttpClient appHttpClient) {
        super(
                ChannelType.DINGTALK,
                orchestratorService,
                new ChannelPolicy(properties.getDingtalk().isRequireMention(), properties.getDingtalk().allowSet()),
                appHttpClient
        );
    }

    @Override
    protected String buildPayload(String text) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of(
                            "title", DingTalkMarkdownFormatter.extractTitle(text),
                            "text", DingTalkMarkdownFormatter.formatMarkdown(text)
                    )
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void send(OutboundMessage message) {
        String target = message.address().target();
        String kind = message.address().kind();
        if (target == null || target.isBlank()) {
            log.warn("[DingTalkChannel] missing target");
            return;
        }
        if (!"webhook".equals(kind) && !"session".equals(kind) && !target.startsWith("http://") && !target.startsWith("https://")) {
            super.send(message);
            return;
        }
        try {
            BotReplier.fromWebhook(target).replyMarkdown(
                    DingTalkMarkdownFormatter.extractTitle(message.text()),
                    DingTalkMarkdownFormatter.formatMarkdown(message.text())
            );
        } catch (Exception ex) {
            log.error("[DingTalkChannel] send via stream sdk failed target={}", target, ex);
            super.send(message);
        }
    }
}
