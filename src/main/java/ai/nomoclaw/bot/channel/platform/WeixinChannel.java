package ai.nomoclaw.bot.channel.platform;

import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import ai.nomoclaw.bot.channel.core.ChannelOrchestratorService;
import ai.nomoclaw.bot.channel.model.ChannelPolicy;
import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.spi.Channel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "agent.channels.weixin", name = "enabled", havingValue = "true")
public class WeixinChannel extends AbstractWebhookChannel implements Channel {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeixinChannel(ChannelOrchestratorService orchestratorService,
                         AgentChannelsProperties properties,
                         HttpClient appHttpClient) {
        super(
                ChannelType.WEIXIN,
                orchestratorService,
                new ChannelPolicy(properties.getWeixin().isRequireMention(), properties.getWeixin().allowSet()),
                appHttpClient
        );
    }

    @Override
    protected String buildPayload(String text) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", text)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
