package ai.nomoclaw.bot.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class NomoClawWorkspaceInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(NomoClawWorkspaceInitializer.class);

    @Override
    public void run(ApplicationArguments args) {
        long startedAt = System.nanoTime();
        NomoClawWorkspaceBootstrap.bootstrap(NomoClawPaths.root());
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("[StartupTiming] workspace.bootstrap elapsedMs={}", elapsedMs);
    }
}
