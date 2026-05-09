package ai.nomoclaw.bot.scheduler;

import ai.nomoclaw.bot.notification.NotificationRequest;
import ai.nomoclaw.bot.notification.NotificationSender;
import ai.nomoclaw.bot.scheduler.config.CronNotifyProperties;
import ai.nomoclaw.bot.store.entity.AgentCronJobEntity;
import ai.nomoclaw.bot.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CronNotificationFanoutService {

    private final NotificationSender notificationSender;
    private final CronSubscriptionRepository subscriptionRepository;
    private final CronNotifyProperties notifyProperties;
    private final CronChannelTargetResolver channelTargetResolver;

    public CronNotificationFanoutService(NotificationSender notificationSender,
                                         CronSubscriptionRepository subscriptionRepository,
                                         CronNotifyProperties notifyProperties,
                                         CronChannelTargetResolver channelTargetResolver) {
        this.notificationSender = notificationSender;
        this.subscriptionRepository = subscriptionRepository;
        this.notifyProperties = notifyProperties;
        this.channelTargetResolver = channelTargetResolver;
    }

    public void fanout(AgentCronJobEntity job, String content, Path reportPath, Instant executedAt, String status) {
        List<TargetRoute> routes = resolveRoutes(job);
        if (routes.isEmpty()) {
            log.info("[CronNotify] no delivery target jobUid={}", job.getJobUid());
            return;
        }
        String title = resolveTitle(job, status);
        String summary = summarizeForIm(content);
        Map<String, String> metadata = buildMetadata(job, status);
        for (TargetRoute route : routes) {
            Map<String, String> routeMetadata = new LinkedHashMap<>(metadata);
            if (!trim(route.botId()).isBlank()) {
                routeMetadata.put("botId", trim(route.botId()));
            }
            sendWithRetry(new NotificationRequest(
                    job.getJobUid(),
                    job.getAgentUid(),
                    title,
                    summary,
                    reportPath,
                    route.channel(),
                    route.target(),
                    executedAt,
                    Map.copyOf(routeMetadata)
            ));
        }
    }

    private void sendWithRetry(NotificationRequest request) {
        int max = Math.max(1, notifyProperties.getRetryMax());
        long baseBackoff = Math.max(1L, notifyProperties.getRetryBackoffMs());
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                notificationSender.send(request);
                return;
            } catch (Exception ex) {
                if (attempt >= max) {
                    log.error("[CronNotify] delivery failed jobUid={} channel={} target={} attempt={}",
                            request.jobUid(), request.channel(), request.target(), attempt, ex);
                    return;
                }
                long waitMs = baseBackoff * (1L << (attempt - 1));
                log.warn("[CronNotify] delivery retry jobUid={} channel={} target={} attempt={}/{} waitMs={} err={}",
                        request.jobUid(), request.channel(), request.target(), attempt, max, waitMs, ex.toString());
                sleep(waitMs);
            }
        }
    }

    private List<TargetRoute> resolveRoutes(AgentCronJobEntity job) {
        List<CronSubscriptionRepository.CronSubscription> subscriptions = subscriptionRepository.listEnabledByJobUid(job.getJobUid());
        if (!subscriptions.isEmpty()) {
            List<TargetRoute> routes = new ArrayList<>();
            for (CronSubscriptionRepository.CronSubscription item : subscriptions) {
                String target = trim(item.target());
                if (target.isBlank()) {
                    target = channelTargetResolver.resolveReplyTarget(item.channel(), job.getConversationUid());
                }
                if (target.isBlank()) {
                    log.warn("[CronNotify] no target found for subscription jobUid={} channel={}", job.getJobUid(), item.channel());
                    continue;
                }
                routes.add(new TargetRoute(item.channel(), target, item.botId()));
            }
            return List.copyOf(routes);
        }
        TargetRoute fallback = loadLegacyRoute(job);
        if (fallback != null) {
            return List.of(fallback);
        }
        CronChannelTargetResolver.ResolvedRoute route = channelTargetResolver.resolveRouteByConversation(job.getConversationUid());
        if (route == null) {
            return List.of();
        }
        return List.of(new TargetRoute(route.channel(), route.target(), ""));
    }

    private TargetRoute loadLegacyRoute(AgentCronJobEntity job) {
        JsonNode node = job.getExtConfig() == null || job.getExtConfig().isBlank()
                ? JsonNodeFactory.instance.objectNode()
                : JsonUtil.fromJsonQuietly(job.getExtConfig(), JsonNode.class).orElse(JsonNodeFactory.instance.objectNode());
        String channel = trim(node.path("notification").path("channel").asString("noop"));
        String target = trim(node.path("notification").path("target").asString(""));
        if (target.isBlank()) {
            return null;
        }
        return new TargetRoute(channel, target, "");
    }

    private String resolveTitle(AgentCronJobEntity job, String status) {
        String title = trim(job.getTitle());
        if (title.isBlank()) {
            title = "定时任务";
        }
        if ("FAILED".equalsIgnoreCase(status)) {
            return title + " 执行失败";
        }
        return title + " 执行完成";
    }

    private String summarizeForIm(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int maxLines = Math.max(1, notifyProperties.getSummaryMaxLines());
        String[] lines = content.replace("\r\n", "\n").split("\n");
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String trimmed = trim(line);
            if (trimmed.isBlank()) {
                continue;
            }
            kept.add(trimmed);
            if (kept.size() >= maxLines) {
                break;
            }
        }
        return String.join("\n", kept);
    }

    private Map<String, String> buildMetadata(AgentCronJobEntity job, String status) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("timezone", trim(job.getTimezone()));
        metadata.put("expression", trim(job.getExpression()));
        metadata.put("status", trim(status));
        return Map.copyOf(metadata);
    }

    private void sleep(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record TargetRoute(String channel, String target, String botId) {
    }
}
