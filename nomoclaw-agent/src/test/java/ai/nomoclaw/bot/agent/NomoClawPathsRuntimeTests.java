package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NomoClawPathsRuntimeTests {

    @TempDir
    Path tempDir;

    private final Path originalRoot = NomoClawPaths.root();

    @AfterEach
    void restoreRoot() {
        NomoClawPaths.configureRoot(originalRoot);
    }

    @Test
    void ensuresRuntimeDirectoriesUnderNomoclawRuntimeRoot() {
        NomoClawPaths.configureRoot(tempDir);

        Path runtimeRoot = NomoClawPaths.ensureRuntimeRoot();
        Path logsRoot = NomoClawPaths.ensureRuntimeLogsRoot();
        Path profilesRoot = NomoClawPaths.ensureRuntimeBrowserProfilesRoot();
        Path browsersRoot = NomoClawPaths.ensureRuntimePlaywrightBrowsersRoot();
        Path pluginsRoot = NomoClawPaths.ensureRuntimePluginsRoot();
        Path tmpRoot = NomoClawPaths.ensureRuntimeTmpRoot();

        assertThat(runtimeRoot).isEqualTo(tempDir.resolve("runtime").toAbsolutePath().normalize());
        assertThat(logsRoot).isEqualTo(runtimeRoot.resolve("logs"));
        assertThat(profilesRoot).isEqualTo(runtimeRoot.resolve("browser-profiles"));
        assertThat(browsersRoot).isEqualTo(runtimeRoot.resolve("playwright-browsers"));
        assertThat(pluginsRoot).isEqualTo(runtimeRoot.resolve("plugins"));
        assertThat(tmpRoot).isEqualTo(runtimeRoot.resolve("tmp"));
        assertThat(Files.isDirectory(runtimeRoot)).isTrue();
        assertThat(Files.isDirectory(logsRoot)).isTrue();
        assertThat(Files.isDirectory(profilesRoot)).isTrue();
        assertThat(Files.isDirectory(browsersRoot)).isTrue();
        assertThat(Files.isDirectory(pluginsRoot)).isTrue();
        assertThat(Files.isDirectory(tmpRoot)).isTrue();
    }
}
