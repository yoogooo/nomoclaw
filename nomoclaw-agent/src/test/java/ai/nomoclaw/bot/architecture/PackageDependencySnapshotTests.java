package ai.nomoclaw.bot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PackageDependencySnapshotTests {

    @Test
    void apiWebLayerShouldNotBeImportedByOrchestrator() throws IOException {
        Path root = Path.of("src/main/java/ai/nomoclaw/bot/orchestrator");
        if (Files.notExists(root)) {
            return;
        }
        List<Path> javaFiles = Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .toList();
        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            assertFalse(content.contains("import ai.nomoclaw.bot.api.controller.")
                            || content.contains("import ai.nomoclaw.bot.api.mapper.")
                            || content.contains("import ai.nomoclaw.bot.api.filter."),
                    () -> "orchestrator depends on api web layer: " + javaFile);
        }
    }
}
