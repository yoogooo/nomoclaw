package ai.nomoclaw.bot.channel.spi;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.model.OutboundMessage;

public interface Channel {

    ChannelType type();

    void start();

    void stop();

    void consume(InboundEnvelope envelope);

    void send(OutboundMessage message);
}
