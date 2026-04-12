package ai.nomoclaw.bot.channel.platform;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.channels.noop", name = "enabled", havingValue = "true", matchIfMissing = false)
// Keep NOOP channel opt-in only to avoid starting idle noop workers in normal runs.
@Slf4j
public class NoopChannel implements Channel {

    @Override
    public ChannelType type() {
        return ChannelType.NOOP;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void consume(InboundEnvelope envelope) {
        log.info("[Channel] noop consume channel={} sessionKey={}", envelope.channel(), envelope.sessionKey());
    }

    @Override
    public void send(OutboundMessage message) {
        log.info("[Channel] noop send channel={} target={} text={}",
                message.channel(),
                message.address().target(),
                message.text());
    }
}
