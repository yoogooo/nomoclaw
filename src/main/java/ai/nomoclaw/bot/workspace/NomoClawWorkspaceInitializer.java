package ai.nomoclaw.bot.workspace;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class NomoClawWorkspaceInitializer implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        NomoClawWorkspaceBootstrap.bootstrap(NomoClawPaths.root());
    }
}
