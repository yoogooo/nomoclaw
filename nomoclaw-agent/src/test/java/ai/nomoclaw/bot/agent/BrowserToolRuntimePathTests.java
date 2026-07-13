package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.tool.BrowserTool;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    @Test
    void removesStalePlaywrightDriverTempDirectories() throws Exception {
        NomoClawPaths.configureRoot(tempDir);
        BrowserTool tool = new BrowserTool(new AgentProperties(), new MessageCancellationRegistry());
        Path driverTmpDir = tempDir.resolve("runtime").resolve("plugins").resolve("browser").resolve("lib");
        Path staleDir = driverTmpDir.resolve("playwright-java-1");
        Path freshDir = driverTmpDir.resolve("playwright-java-2");
        Path unrelatedDir = driverTmpDir.resolve("other-temp");
        Files.createDirectories(staleDir);
        Files.createDirectories(freshDir);
        Files.createDirectories(unrelatedDir);
        Files.writeString(staleDir.resolve("driver.txt"), "stale");
        Files.writeString(freshDir.resolve("driver.txt"), "fresh");
        Files.setLastModifiedTime(staleDir, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
        Files.setLastModifiedTime(freshDir, FileTime.from(Instant.now()));

        invoke(tool, "configurePlaywrightDriverTmpDirectory");

        assertThat(staleDir).doesNotExist();
        assertThat(freshDir).isDirectory();
        assertThat(unrelatedDir).isDirectory();
    }

    @Test
    void waitForShouldRejectBlankSelector() throws Exception {
        BrowserTool tool = new BrowserTool(new AgentProperties(), new MessageCancellationRegistry());
        ToolRequest request = toolRequest("wait_for", "");
        Page page = Mockito.mock(Page.class);
        Object managedMode = browserMode("MANAGED");

        ToolResult result = (ToolResult) invoke(tool, "executeAction", request, page, 0L, "profile", managedMode);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_ARGS");
        assertThat(result.errorMessage()).isEqualTo("selector is required for wait_for");
        Mockito.verifyNoInteractions(page);
    }

    private ToolRequest toolRequest(String action, String selector) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("action", action);
        args.put("selector", selector);
        return new ToolRequest(
                "conversation-1",
                "message-1",
                "step-1",
                "agent-1",
                "agent",
                tempDir,
                tempDir.resolve("tmp"),
                tempDir.resolve("report"),
                args,
                5_000L,
                null
        );
    }

    private Object browserMode(String name) throws Exception {
        Class<?> browserModeClass = Class.forName("ai.nomoclaw.bot.tool.BrowserTool$BrowserMode");
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object value = Enum.valueOf((Class<? extends Enum>) browserModeClass.asSubclass(Enum.class), name);
        return value;
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
