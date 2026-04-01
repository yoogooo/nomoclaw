package ai.nomoclaw.bot.channel.core;

import ai.nomoclaw.bot.channel.model.ChannelAddress;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.spi.ChannelMessageRouter;
import ai.nomoclaw.bot.channel.spi.ChannelMessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DefaultChannelMessageRouter implements ChannelMessageRouter {

    private final Map<ChannelType, ChannelMessageSender> senders;

    public DefaultChannelMessageRouter(List<ChannelMessageSender> senderList) {
        Map<ChannelType, ChannelMessageSender> mapping = new EnumMap<>(ChannelType.class);
        for (ChannelMessageSender sender : senderList) {
            mapping.put(sender.channelType(), sender);
        }
        this.senders = Map.copyOf(mapping);
    }

    @Override
    public void send(ChannelType channel, String target, String text, Map<String, String> metadata) {
        ChannelMessageSender sender = senders.get(channel);
        if (sender == null) {
            log.warn("[ChannelRouter] no channel found type={} target={}", channel, target);
            return;
        }
        sender.send(new OutboundMessage(
                channel,
                ChannelAddress.parse(channel, target),
                text,
                metadata
        ));
    }
}
