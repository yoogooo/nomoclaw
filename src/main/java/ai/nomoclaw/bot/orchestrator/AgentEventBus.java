package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.model.AgentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class AgentEventBus {

    private final Map<String, List<SseEmitter>> emittersByConversation = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String conversationUid) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByConversation.computeIfAbsent(conversationUid, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("[SSE] subscribe conversationUid={} subscribers={}", conversationUid, emittersByConversation.get(conversationUid).size());
        emitter.onCompletion(() -> removeEmitter(conversationUid, emitter));
        emitter.onTimeout(() -> removeEmitter(conversationUid, emitter));
        emitter.onError(e -> removeEmitter(conversationUid, emitter));
        return emitter;
    }

    public void publish(AgentEvent event) {
        List<SseEmitter> emitters = emittersByConversation.getOrDefault(event.conversationUid(), List.of());
        log.info("[SSE] publish eventType={} conversationUid={} messageUid={} stepUid={} subscribers={}",
                event.eventType(), event.conversationUid(), event.messageUid(), event.stepUid(), emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.eventType().name().toLowerCase())
                        .id(event.id())
                        .data(event));
            } catch (Exception ex) {
                if (isClientDisconnected(ex)) {
                    log.debug("[SSE] client disconnected conversationUid={} eventUid={} err={}",
                            event.conversationUid(), event.id(), ex.getMessage());
                } else {
                    log.warn("[SSE] publish failed conversationUid={} eventUid={} err={}",
                            event.conversationUid(), event.id(), ex.getMessage(), ex);
                }
                removeEmitter(event.conversationUid(), emitter);
            }
        }
    }

    private boolean isClientDisconnected(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof IOException || current instanceof AsyncRequestNotUsableException) {
                String message = current.getMessage();
                if (message != null && message.toLowerCase().contains("broken pipe")) {
                    return true;
                }
                if (current instanceof AsyncRequestNotUsableException) {
                    return true;
                }
            }
            if (current instanceof IllegalStateException) {
                String message = current.getMessage();
                if (message != null && message.toLowerCase().contains("failed to send")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void removeEmitter(String conversationUid, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByConversation.get(conversationUid);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByConversation.remove(conversationUid);
        }
        log.info("[SSE] unsubscribe conversationUid={} remaining={}", conversationUid, emittersByConversation.getOrDefault(conversationUid, List.of()).size());
    }
}
