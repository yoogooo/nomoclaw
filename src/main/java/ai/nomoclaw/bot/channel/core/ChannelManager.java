package ai.nomoclaw.bot.channel.core;

import ai.nomoclaw.bot.channel.model.ChannelType;
import ai.nomoclaw.bot.channel.model.InboundEnvelope;
import ai.nomoclaw.bot.channel.spi.Channel;
import ai.nomoclaw.bot.channel.config.AgentChannelsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class ChannelManager implements SmartLifecycle {

    private final ChannelRegistry channelRegistry;
    private final AgentChannelsProperties properties;
    private final Map<ChannelType, LinkedBlockingQueue<InboundEnvelope>> queues = new ConcurrentHashMap<>();
    private final Map<ChannelType, ExecutorService> workers = new ConcurrentHashMap<>();
    private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();
    private volatile boolean running;

    public ChannelManager(ChannelRegistry channelRegistry, AgentChannelsProperties properties) {
        this.channelRegistry = channelRegistry;
        this.properties = properties;
    }

    public void enqueue(InboundEnvelope envelope) {
        if (!properties.isEnabled()) {
            return;
        }
        LinkedBlockingQueue<InboundEnvelope> queue = queues.get(envelope.channel());
        if (queue == null) {
            log.warn("[ChannelManager] queue not found channel={}", envelope.channel());
            return;
        }
        boolean accepted = queue.offer(envelope);
        if (!accepted) {
            log.warn("[ChannelManager] queue full channel={} sessionKey={}", envelope.channel(), envelope.sessionKey());
        }
    }

    @Override
    public void start() {
        if (running || !properties.isEnabled()) {
            return;
        }
        for (Channel channel : channelRegistry.all()) {
            queues.put(channel.type(), new LinkedBlockingQueue<>(properties.getQueueSize()));
            channel.start();
            ExecutorService executor = Executors.newFixedThreadPool(
                    properties.getWorkers(),
                    r -> {
                        Thread thread = new Thread(r);
                        thread.setName("channel-" + channel.type().value() + "-worker");
                        thread.setDaemon(true);
                        return thread;
                    }
            );
            workers.put(channel.type(), executor);
            for (int i = 0; i < properties.getWorkers(); i++) {
                executor.submit(() -> consumeLoop(channel));
            }
        }
        running = true;
        log.info("[ChannelManager] started channels={} workers={} queueSize={}", channelRegistry.all().size(), properties.getWorkers(), properties.getQueueSize());
    }

    private void consumeLoop(Channel channel) {
        LinkedBlockingQueue<InboundEnvelope> queue = queues.get(channel.type());
        if (queue == null) {
            return;
        }
        while (!Thread.currentThread().isInterrupted()) {
            try {
                InboundEnvelope envelope = queue.take();
                String lockKey = envelope.channel().value() + ":" + envelope.sessionKey();
                Object lock = keyLocks.computeIfAbsent(lockKey, ignored -> new Object());
                synchronized (lock) {
                    channel.consume(envelope);
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.error("[ChannelManager] consume failed channel={}", channel.type(), ex);
            }
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        workers.values().forEach(ExecutorService::shutdownNow);
        workers.clear();
        queues.clear();
        channelRegistry.all().forEach(Channel::stop);
        keyLocks.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
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
