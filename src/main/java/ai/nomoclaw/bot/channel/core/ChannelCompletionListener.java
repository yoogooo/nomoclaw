package ai.nomoclaw.bot.channel.core;

import ai.nomoclaw.bot.channel.model.ChannelMessageCompletedEvent;
import ai.nomoclaw.bot.channel.spi.ChannelMessageRouter;
import ai.nomoclaw.bot.model.MessageStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class ChannelCompletionListener {

    private final ChannelPendingReplyContextStore pendingReplyContextStore;
    private final ChannelMessageRouter channelMessageRouter;

    public ChannelCompletionListener(ChannelPendingReplyContextStore pendingReplyContextStore,
                                     ChannelMessageRouter channelMessageRouter) {
        this.pendingReplyContextStore = pendingReplyContextStore;
        this.channelMessageRouter = channelMessageRouter;
    }

    @EventListener
    public void onMessageCompleted(ChannelMessageCompletedEvent event) {
        ChannelPendingReplyContextStore.PendingReplyContext context = pendingReplyContextStore.get(event.messageUid()).orElse(null);
        if (context == null) {
            return;
        }
        if (context.replyTarget() == null || context.replyTarget().isBlank()) {
            pendingReplyContextStore.remove(event.messageUid());
            return;
        }
        String reply = resolveReply(event);
        if (reply.isBlank()) {
            pendingReplyContextStore.remove(event.messageUid());
            return;
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("conversationUid", context.conversationUid());
        metadata.put("messageUid", event.messageUid());
        metadata.put("status", event.status().name());
        try {
            channelMessageRouter.send(context.channel(), context.replyTarget(), reply, Map.copyOf(metadata));
            pendingReplyContextStore.remove(event.messageUid());
        } catch (Exception ex) {
            log.error("[ChannelCompletion] send failed channel={} messageUid={} target={}",
                    context.channel(), event.messageUid(), context.replyTarget(), ex);
        }
    }

    private String resolveReply(ChannelMessageCompletedEvent event) {
        String reply = event.finalReply() == null ? "" : event.finalReply().trim();
        if (!reply.isBlank()) {
            return reply;
        }
        if (event.status() == MessageStatus.FAILED) {
            return "任务执行失败，请稍后重试。";
        }
        if (event.status() == MessageStatus.CANCELED) {
            return "任务已取消。";
        }
        return "";
    }
}
