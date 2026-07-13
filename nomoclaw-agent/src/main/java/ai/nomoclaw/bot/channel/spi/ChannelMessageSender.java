package ai.nomoclaw.bot.channel.spi;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;

public interface ChannelMessageSender {

    ChannelType channelType();

    void send(OutboundMessage message);
}
