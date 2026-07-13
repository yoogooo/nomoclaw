package ai.nomoclaw.bot.channel.platform;

import ai.nomoclaw.bot.channel.model.ChannelPolicy;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.model.OutboundMessage;
import ai.nomoclaw.bot.channel.core.ChannelOrchestratorService;
import ai.nomoclaw.bot.channel.spi.Channel;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
public abstract class AbstractWebhookChannel implements Channel {

    private final ChannelType type;
    private final ChannelOrchestratorService orchestratorService;
    private final ChannelPolicy policy;
    private final HttpClient httpClient;

    protected AbstractWebhookChannel(ChannelType type,
                                     ChannelOrchestratorService orchestratorService,
                                     ChannelPolicy policy,
                                     HttpClient httpClient) {
        this.type = type;
        this.orchestratorService = orchestratorService;
        this.policy = policy;
        this.httpClient = httpClient;
    }

    @Override
    public ChannelType type() {
        return type;
    }

    @Override
    public void start() {
        log.info("[Channel] started type={}", type);
    }

    @Override
    public void stop() {
        log.info("[Channel] stopped type={}", type);
    }

    @Override
    public void consume(InboundEnvelope envelope) {
        if (!policy.allows(envelope.senderId())) {
            log.info("[Channel] sender blocked type={} sender={}", type, envelope.senderId());
            return;
        }
        if (envelope.isGroupChat() && !envelope.mentioned()) {
            log.info("[Channel] group message ignored without bot mention type={} sessionKey={}", type, envelope.sessionKey());
            return;
        }
        if (policy.requireMention() && !envelope.mentioned()) {
            log.debug("[Channel] mention required type={} sessionKey={}", type, envelope.sessionKey());
            return;
        }
        orchestratorService.processInbound(envelope);
    }

    @Override
    public void send(OutboundMessage message) {
        String target = message.address().target();
        if (target == null || target.isBlank()) {
            log.warn("[Channel] missing target type={}", type);
            return;
        }
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            log.warn("[Channel] unsupported target type={} target={}", type, target);
            return;
        }
        try {
            String body = buildPayload(message.text());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("[Channel] send failed type={} status={} target={} body={}", type, response.statusCode(), target, response.body());
            }
        } catch (Exception ex) {
            log.error("[Channel] send error type={} target={}", type, target, ex);
        }
    }

    protected abstract String buildPayload(String text);
}
