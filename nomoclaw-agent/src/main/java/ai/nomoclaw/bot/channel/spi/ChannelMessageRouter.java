package ai.nomoclaw.bot.channel.spi;

import ai.nomoclaw.bot.channel.model.ChannelType;

import java.util.Map;

public interface ChannelMessageRouter {

    void send(ChannelType channel, String target, String text, Map<String, String> metadata);
}
