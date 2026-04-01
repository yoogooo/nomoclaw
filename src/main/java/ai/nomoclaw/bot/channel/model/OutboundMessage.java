package ai.nomoclaw.bot.channel.model;

import java.util.Map;

public record OutboundMessage(
        ChannelType channel,
        ChannelAddress address,
        String text,
        Map<String, String> metadata
) {

    public OutboundMessage {
        text = text == null ? "" : text;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
