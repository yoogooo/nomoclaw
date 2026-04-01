package ai.nomoclaw.bot.orchestrator;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageCancellationRegistry {

    private final Set<String> canceledMessageIds = ConcurrentHashMap.newKeySet();

    public void cancel(String messageUid) {
        canceledMessageIds.add(messageUid);
    }

    public boolean isCanceled(String messageUid) {
        return canceledMessageIds.contains(messageUid);
    }

    public void clear(String messageUid) {
        canceledMessageIds.remove(messageUid);
    }
}
