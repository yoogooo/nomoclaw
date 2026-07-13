package ai.nomoclaw.bot.channel.platform.sender;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.ChannelMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NoopChannelMessageSender implements ChannelMessageSender {

    @Override
    public ChannelType channelType() {
        return ChannelType.NOOP;
    }

    @Override
    public void send(OutboundMessage message) {
        log.info("[ChannelSender] noop send channel={} target={} text={}",
                message.channel(),
                message.address().target(),
                message.text());
    }
}
