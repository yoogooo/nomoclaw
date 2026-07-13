package ai.nomoclaw.bot.startup;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MacAppBrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(MacAppBrowserLauncher.class);
    private final AtomicBoolean launched = new AtomicBoolean(false);
    private final boolean enabled;
    private final String url;

    public MacAppBrowserLauncher(
            @Value("${nomoclaw.desktop.open-browser-on-startup:false}") boolean enabled,
            @Value("${nomoclaw.desktop.open-browser-url:http://localhost:18080/nomoclaw/#/}") String url) {
        this.enabled = enabled;
        this.url = url;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled || !launched.compareAndSet(false, true)) {
            return;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("mac")) {
            return;
        }
        try {
            new ProcessBuilder("/usr/bin/open", url).start();
            log.info("Opened browser url={}", url);
        } catch (IOException ex) {
            log.warn("Failed to open browser url={}", url, ex);
        }
    }
}
