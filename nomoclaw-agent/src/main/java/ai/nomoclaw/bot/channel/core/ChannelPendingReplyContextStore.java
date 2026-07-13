package ai.nomoclaw.bot.channel.core;

import ai.nomoclaw.bot.channel.model.ChannelType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ChannelPendingReplyContextStore {

    private static final Duration CONTEXT_TTL = Duration.ofMinutes(30);

    private final ConcurrentMap<String, PendingReplyContext> contexts = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupExecutor;

    @PostConstruct
    public void startCleanupLoop() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "channel-pending-reply-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpired, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void destroy() {
        ScheduledExecutorService executor = cleanupExecutor;
        cleanupExecutor = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public void put(String messageUid, ChannelType channel, String replyTarget, String conversationUid, String botId) {
        if (messageUid == null || messageUid.isBlank()) {
            return;
        }
        contexts.put(messageUid, new PendingReplyContext(
                messageUid,
                channel,
                replyTarget == null ? "" : replyTarget.trim(),
                conversationUid == null ? "" : conversationUid.trim(),
                botId == null ? "" : botId.trim(),
                Instant.now()
        ));
    }

    public Optional<PendingReplyContext> get(String messageUid) {
        return Optional.ofNullable(contexts.get(messageUid));
    }

    public Optional<PendingReplyContext> remove(String messageUid) {
        return Optional.ofNullable(contexts.remove(messageUid));
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        contexts.entrySet().removeIf(entry -> now.isAfter(entry.getValue().createdAt().plus(CONTEXT_TTL)));
    }

    public record PendingReplyContext(
            String messageUid,
            ChannelType channel,
            String replyTarget,
            String conversationUid,
            String botId,
            Instant createdAt
    ) {
    }
}
