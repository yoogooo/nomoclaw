package ai.nomoclaw.bot.startup;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MacAppLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(MacAppLifecycleManager.class);

    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    public MacAppLifecycleManager(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!isMacOs() || GraphicsEnvironment.isHeadless() || !registered.compareAndSet(false, true)) {
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit();
                if (!Desktop.isDesktopSupported()) {
                    return;
                }
                Desktop.getDesktop().setQuitHandler((event, response) -> {
                    response.cancelQuit();
                    Thread shutdownThread = new Thread(() -> {
                        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
                        System.exit(exitCode);
                    }, "mac-quit-handler");
                    shutdownThread.setDaemon(true);
                    shutdownThread.start();
                });
                log.info("Registered macOS quit handler");
            } catch (Exception ex) {
                log.warn("Failed to register macOS quit handler", ex);
            }
        });
    }

    private boolean isMacOs() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("mac");
    }
}
