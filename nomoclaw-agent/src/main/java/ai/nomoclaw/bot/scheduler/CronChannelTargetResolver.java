package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.store.entity.AgentChannelSessionEntity;
import ai.nomoclaw.bot.channel.store.repository.AgentChannelSessionRepository;
import org.springframework.stereotype.Component;

@Component
public class CronChannelTargetResolver {

    private final AgentChannelSessionRepository channelSessionRepository;

    public CronChannelTargetResolver(AgentChannelSessionRepository channelSessionRepository) {
        this.channelSessionRepository = channelSessionRepository;
    }

    public String resolveLatestReplyTarget(String channel) {
        String normalized = ChannelType.from(channel).value();
        if ("noop".equals(normalized) || "web".equals(normalized)) {
            return "";
        }
        AgentChannelSessionEntity entity = channelSessionRepository.findLatestByChannel(normalized);
        if (entity == null || entity.getReplyTarget() == null) {
            return "";
        }
        return entity.getReplyTarget().trim();
    }

    public String resolveReplyTarget(String channel, String conversationUid) {
        String normalized = ChannelType.from(channel).value();
        if ("noop".equals(normalized) || "web".equals(normalized)) {
            return "";
        }
        String normalizedConversationUid = conversationUid == null ? "" : conversationUid.trim();
        if (!normalizedConversationUid.isBlank()) {
            AgentChannelSessionEntity byConversation = channelSessionRepository
                    .findLatestByChannelAndConversationUid(normalized, normalizedConversationUid);
            if (byConversation != null && byConversation.getReplyTarget() != null && !byConversation.getReplyTarget().isBlank()) {
                return byConversation.getReplyTarget().trim();
            }
        }
        return resolveLatestReplyTarget(normalized);
    }

    public ResolvedRoute resolveRouteByConversation(String conversationUid) {
        String normalizedConversationUid = conversationUid == null ? "" : conversationUid.trim();
        if (normalizedConversationUid.isBlank()) {
            return null;
        }
        AgentChannelSessionEntity entity = channelSessionRepository.findLatestByConversationUid(normalizedConversationUid);
        if (entity == null) {
            return null;
        }
        String channel = ChannelType.from(entity.getChannel()).value();
        String target = entity.getReplyTarget() == null ? "" : entity.getReplyTarget().trim();
        if (target.isBlank() || "noop".equals(channel) || "web".equals(channel)) {
            return null;
        }
        return new ResolvedRoute(channel, target);
    }

    public record ResolvedRoute(String channel, String target) {
    }
}
