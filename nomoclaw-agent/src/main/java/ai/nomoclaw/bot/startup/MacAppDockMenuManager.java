package ai.nomoclaw.bot.startup;

import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MacAppDockMenuManager {

    private static final Logger log = LoggerFactory.getLogger(MacAppDockMenuManager.class);

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final String url;

    public MacAppDockMenuManager(
            @Value("${nomoclaw.desktop.open-browser-url:http://localhost:18080/nomoclaw/#/}") String url) {
        this.url = url;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!isMacOs() || GraphicsEnvironment.isHeadless() || !registered.compareAndSet(false, true)) {
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                Toolkit.getDefaultToolkit();
                PopupMenu dockMenu = new PopupMenu();
                MenuItem openWebItem = new MenuItem("打开 NomoClaw 页面");
                openWebItem.addActionListener(event -> openWebConsole());
                dockMenu.add(openWebItem);
                setDockMenu(dockMenu);
                log.info("Registered macOS dock menu");
            } catch (Exception ex) {
                log.warn("Failed to register macOS dock menu", ex);
            }
        });
    }

    private void openWebConsole() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            new ProcessBuilder("/usr/bin/open", url).start();
        } catch (Exception ex) {
            log.warn("Failed to open browser url={}", url, ex);
        }
    }

    private void setDockMenu(PopupMenu dockMenu) throws Exception {
        if (Taskbar.isTaskbarSupported()) {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.MENU)) {
                taskbar.setMenu(dockMenu);
                return;
            }
        }
        Class<?> appClass = Class.forName("com.apple.eawt.Application");
        Object app = appClass.getMethod("getApplication").invoke(null);
        appClass.getMethod("setDockMenu", PopupMenu.class).invoke(app, dockMenu);
    }

    private boolean isMacOs() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("mac");
    }
}
