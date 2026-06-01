package ai.nomoclaw.bot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiPackageLayoutTests {

    @Test
    void apiRootShouldOnlyContainExceptionHandler() throws IOException {
        Path apiRoot = Path.of("src/main/java/ai/nomoclaw/bot/api");
        if (Files.notExists(apiRoot)) {
            return;
        }
        List<String> rootJavaFiles = Files.list(apiRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> path.getFileName().toString())
                .sorted()
                .toList();

        assertTrue(rootJavaFiles.equals(List.of("AgentExceptionHandler.java")),
                () -> "Unexpected api root java files: " + rootJavaFiles);
    }
}
