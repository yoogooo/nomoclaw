package ai.nomoclaw.bot.channel.platform.sender;

import ai.nomoclaw.bot.channel.config.ChannelBotCredentialResolver;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.ChannelMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.channels.wecom", name = "enabled", havingValue = "true")
@Slf4j
public class WeComChannelMessageSender implements ChannelMessageSender {

    private final ChannelBotCredentialResolver credentialResolver;

    public WeComChannelMessageSender(ChannelBotCredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.WECOM;
    }

    @Override
    public void send(OutboundMessage message) {
        String requestedBotId = message.metadata().getOrDefault("botId", "");
        ChannelBotCredentialResolver.WeComBotCredential credential = credentialResolver.resolveWeCom(requestedBotId);
        if (credential == null || credential.wecomBotId().isBlank() || credential.secret().isBlank()) {
            log.warn("[WeComSender] missing AI Bot botId/secret");
            return;
        }
        log.warn("[WeComSender] WeCom AI Bot uses the QwenPaw aibot WebSocket reply channel; Java WS bridge is not wired yet");
    }
}
