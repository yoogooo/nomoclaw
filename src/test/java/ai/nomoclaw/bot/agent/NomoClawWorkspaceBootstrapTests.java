package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.workspace.NomoClawWorkspaceBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NomoClawWorkspaceBootstrapTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateDefaultWorkspaceOnBootstrap() throws Exception {
        NomoClawWorkspaceBootstrap.bootstrap(tempDir);

        Path defaultAgent = tempDir.resolve("agents").resolve("default");
        assertTrue(Files.isDirectory(defaultAgent));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("AGENT.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("SOUL.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("IDENTITY.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("USER.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("TOOLS.md")));
        assertTrue(Files.isRegularFile(defaultAgent.resolve("MEMORY.md")));
        assertTrue(Files.isDirectory(defaultAgent.resolve("tmp")));
        assertTrue(Files.isDirectory(defaultAgent.resolve("report")));
    }
}
