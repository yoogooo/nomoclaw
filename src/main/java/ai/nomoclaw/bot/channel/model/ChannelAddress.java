package ai.nomoclaw.bot.channel.model;

import java.util.Map;

public record ChannelAddress(
        ChannelType channel,
        String kind,
        String target,
        Map<String, String> metadata
) {

    public ChannelAddress {
        kind = normalize(kind, "default");
        target = normalize(target, "");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ChannelAddress parse(ChannelType channel, String raw) {
        String text = normalize(raw, "");
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return new ChannelAddress(channel, "webhook", text, Map.of());
        }
        String prefix = channel.value() + ":";
        if (!text.startsWith(prefix)) {
            return new ChannelAddress(channel, "default", text, Map.of());
        }
        String remain = text.substring(prefix.length());
        int idx = remain.indexOf(':');
        if (idx < 0) {
            return new ChannelAddress(channel, "default", remain, Map.of());
        }
        String kind = remain.substring(0, idx);
        String target = remain.substring(idx + 1);
        return new ChannelAddress(channel, kind, target, Map.of());
    }

    private static String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
