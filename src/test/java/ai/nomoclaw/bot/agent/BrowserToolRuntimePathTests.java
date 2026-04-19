package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.tool.BrowserTool;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserToolRuntimePathTests {

    @TempDir
    Path tempDir;

    private Path originalRoot;
    private String originalOsName;
    private String originalPlaywrightDriverTmpDir;

    @BeforeEach
    void rememberGlobals() {
        originalRoot = NomoClawPaths.root();
        originalOsName = System.getProperty("os.name");
        originalPlaywrightDriverTmpDir = System.getProperty("playwright.driver.tmpdir");
    }

    @AfterEach
    void restoreGlobals() {
        NomoClawPaths.configureRoot(originalRoot);
        if (originalOsName == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", originalOsName);
        }
        if (originalPlaywrightDriverTmpDir == null) {
            System.clearProperty("playwright.driver.tmpdir");
        } else {
            System.setProperty("playwright.driver.tmpdir", originalPlaywrightDriverTmpDir);
        }
    }

    @Test
    void resolvesRuntimeProfileCacheAndPluginPaths() throws Exception {
        NomoClawPaths.configureRoot(tempDir);
        System.setProperty("os.name", "Linux");
        BrowserTool tool = new BrowserTool(new AgentProperties(), new MessageCancellationRegistry());

        Path profileDir = (Path) invoke(tool, "profileDirectory", "agent_a");
        Path cacheRoot = (Path) invoke(tool, "resolvePlaywrightCacheRoot");
        invoke(tool, "configurePlaywrightDriverTmpDirectory");
        String configuredDriverTmpDir = System.getProperty("playwright.driver.tmpdir");
        @SuppressWarnings("unchecked")
        Map<String, String> playwrightEnv = (Map<String, String>) invoke(tool, "buildPlaywrightEnv", cacheRoot);
        String configuredTmpDir = playwrightEnv.get("TMPDIR");

        assertThat(profileDir).isEqualTo(tempDir.resolve("runtime").resolve("browser-profiles").resolve("agent_a").toAbsolutePath().normalize());
        assertThat(cacheRoot).isEqualTo(tempDir.resolve("runtime").resolve("playwright-browsers").toAbsolutePath().normalize());
        assertThat(configuredDriverTmpDir).isEqualTo(
                tempDir.resolve("runtime").resolve("plugins").resolve("browser").resolve("lib").toAbsolutePath().normalize().toString()
        );
        assertThat(configuredTmpDir).isEqualTo(
                tempDir.resolve("runtime").resolve("tmp").resolve("playwright-driver").toAbsolutePath().normalize().toString()
        );
        assertThat(Files.isDirectory(Path.of(configuredDriverTmpDir))).isTrue();
        assertThat(Files.isDirectory(Path.of(configuredTmpDir))).isTrue();
    }

    private Object invoke(BrowserTool tool, String methodName, Object... args) throws Exception {
        Method method = null;
        for (Method candidate : BrowserTool.class.getDeclaredMethods()) {
            if (!candidate.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if (parameterTypes.length != args.length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    continue;
                }
                if (!parameterTypes[i].isAssignableFrom(args[i].getClass())) {
                    match = false;
                    break;
                }
            }
            if (match) {
                method = candidate;
                break;
            }
        }
        if (method == null) {
            throw new NoSuchMethodException(BrowserTool.class.getName() + "." + methodName);
        }
        method.setAccessible(true);
        return method.invoke(tool, args);
    }
}
