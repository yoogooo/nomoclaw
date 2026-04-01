package ai.nomoclaw.bot.channel.stream;

import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ChannelStreamBootstrap implements SmartLifecycle {

    private final List<ChannelStreamConnector> connectors;
    private final AgentChannelsProperties properties;
    private volatile boolean running;

    public ChannelStreamBootstrap(List<ChannelStreamConnector> connectors, AgentChannelsProperties properties) {
        this.connectors = connectors;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (running || !properties.isEnabled()) {
            return;
        }
        for (ChannelStreamConnector connector : connectors) {
            try {
                connector.start();
                log.info("[ChannelStream] connector started {}", connector.name());
            } catch (Exception ex) {
                log.error("[ChannelStream] connector start failed {}", connector.name(), ex);
            }
        }
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        connectors.forEach(ChannelStreamConnector::stop);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 50;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }
}
