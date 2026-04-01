package ai.nomoclaw.bot.channel.spi;

import ai.nomoclaw.bot.channel.model.ChannelSessionKey;

import java.util.Map;
import java.util.Optional;

public interface ChannelSessionRepository {

    Optional<ChannelSessionRecord> find(ChannelSessionKey key);

    ChannelSessionRecord upsert(ChannelSessionRecord record);

    record ChannelSessionRecord(
            ChannelSessionKey key,
            String conversationUid,
            String replyTarget,
            Map<String, String> routeMetadata
    ) {
    }
}
