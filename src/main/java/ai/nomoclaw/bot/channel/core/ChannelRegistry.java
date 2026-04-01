package ai.nomoclaw.bot.channel.core;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.spi.Channel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ChannelRegistry {

    private final Map<ChannelType, Channel> channels;

    public ChannelRegistry(List<Channel> channelList) {
        Map<ChannelType, Channel> mapping = new EnumMap<>(ChannelType.class);
        for (Channel channel : channelList) {
            mapping.put(channel.type(), channel);
        }
        this.channels = Map.copyOf(mapping);
    }

    public Optional<Channel> get(ChannelType type) {
        return Optional.ofNullable(channels.get(type));
    }

    public List<Channel> all() {
        return channels.values().stream().toList();
    }
}
