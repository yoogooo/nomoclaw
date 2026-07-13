package ai.nomoclaw.bot.workspace;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class NomoClawPathConfigurer {

    private final NomoClawProperties properties;

    public NomoClawPathConfigurer(NomoClawProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void configure() {
        String configured = properties.getRootDir() == null ? "" : properties.getRootDir().trim();
        if (configured.isBlank()) {
            NomoClawPaths.configureRoot(null);
            return;
        }
        NomoClawPaths.configureRoot(Path.of(configured));
    }
}
