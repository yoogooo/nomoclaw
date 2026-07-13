package ai.nomoclaw.bot.channel.spi;

import ai.nomoclaw.bot.channel.model.InboundEnvelope;

import java.util.Map;

public interface ChannelMessageConverter {

    InboundEnvelope convert(Map<String, Object> payload);
}
