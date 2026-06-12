package ai.nomoclaw.bot.tool;

import ai.nomoclaw.bot.util.UuidUtil;

import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browser tool backed by Playwright persistent context.
 *
 * <p>Important lifecycle note:
 * do NOT call {@code com.microsoft.playwright.CLI.main(...)} from this JVM.
 * In Playwright Java 1.58.0, {@code CLI.main} eventually calls {@code System.exit(code)},
 * which terminates the whole Spring Boot backend process after install commands finish.
 * Chromium installation must run in an isolated child process (see
 * {@link #runPlaywrightCli(Map, Consumer, String...)}). The child process and the runtime
 * Playwright instance must share the same browser cache environment, otherwise
 * progress will be measured against one directory while the actual download is
 * written to another.
 */
@Component
@Slf4j
public class BrowserTool implements Tool {
    private static final long DOWNLOAD_REPORT_INTERVAL_MS = 800L;
    private static final long DOWNLOAD_REPORT_MIN_DELTA_BYTES = 256L * 1024L;
    private static final long DOWNLOAD_REPORT_FORCE_INTERVAL_MS = 2200L;
    private static final long ESTIMATED_BROWSER_DOWNLOAD_BYTES = 520L * 1024L * 1024L;
    private static final long WINDOWS_CDP_CONNECT_TIMEOUT_MS = 10_000L;
    private static final long WINDOWS_CDP_CONNECT_RETRY_INTERVAL_MS = 200L;
    private static final Pattern PLAYWRIGHT_PROGRESS_PATTERN = Pattern.compile("(\\d{1,3})%\\s+of\\s+.+");
    private static final Pattern PLAYWRIGHT_PROGRESS_WITH_SIZE_PATTERN = Pattern.compile("(\\d{1,3})%\\s+of\\s+([0-9]+(?:\\.[0-9]+)?)\\s*([KMG]?i?B)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYWRIGHT_DOWNLOAD_START_PATTERN = Pattern.compile("^Downloading\\s+(.+?)\\s+from\\s+.+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYWRIGHT_DOWNLOAD_DONE_PATTERN = Pattern.compile("^(.+?)\\s+downloaded\\s+to\\s+.+$", Pattern.CASE_INSENSITIVE);
    private static final long PLAYWRIGHT_UNKNOWN_ARTIFACT_ESTIMATED_BYTES = 32L * 1024L * 1024L;
    private static final String PLAYWRIGHT_DRIVER_TEMP_PREFIX = "playwright-java-";
    private static final Duration PLAYWRIGHT_DRIVER_TEMP_RETENTION = Duration.ofHours(6);
    private static final List<String> PLAYWRIGHT_DEFAULT_ARTIFACTS = List.of(
            "chromium",
            "ffmpeg",
            "chromium_headless_shell"
    );
    private static final Map<String, Long> PLAYWRIGHT_ARTIFACT_ESTIMATED_BYTES = Map.of(
            "chromium", 170L * 1024L * 1024L,
            "ffmpeg", 2L * 1024L * 1024L,
            "chromium_headless_shell", 96L * 1024L * 1024L
    );
    private static final List<String> WINDOWS_CHROME_STABLE_ARGS = List.of(
            "--disable-background-networking",
            "--disable-background-timer-throttling",
            "--disable-backgrounding-occluded-windows",
            "--disable-breakpad",
            "--disable-client-side-phishing-detection",
            "--disable-component-extensions-with-background-pages",
            "--disable-component-update",
            "--disable-default-apps",
            "--disable-dev-shm-usage",
            "--disable-popup-blocking",
            "--disable-prompt-on-repost",
            "--disable-renderer-backgrounding",
            "--disable-sync",
            "--metrics-recording-only",
            "--no-default-browser-check",
            "--no-first-run",
            "--password-store=basic",
            "--use-mock-keychain"
    );

    private final Map<String, BrowserContext> contextByProfile = new ConcurrentHashMap<>();
    private final Map<String, Browser> browserByProfile = new ConcurrentHashMap<>();
    private final Map<String, Process> processByProfile = new ConcurrentHashMap<>();
    private final Map<String, String> profileByConversation = new ConcurrentHashMap<>();
    private final Map<String, Page> pageByConversation = new ConcurrentHashMap<>();
    private final Map<String, BrowserMode> modeByConversation = new ConcurrentHashMap<>();
    private final AgentProperties agentProperties;
    private final MessageCancellationRegistry cancellationRegistry;
    private volatile boolean chromiumInstallEnsured;
    private volatile Playwright playwright;

    public BrowserTool(AgentProperties agentProperties,
                       MessageCancellationRegistry cancellationRegistry) {
        this.agentProperties = agentProperties;
        this.cancellationRegistry = cancellationRegistry;
    }

    @Override
    public String name() {
        return "BrowserTool";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        long start = System.currentTimeMillis();
        String profileKey = profileByConversation.computeIfAbsent(
                request.conversationUid(),
                ignored -> resolveProfileKey(request)
        );
        try {
            if (cancellationRegistry.isCanceled(request.messageUid())) {
                return ToolResult.failure("CANCELLED", "message canceled", metric(start));
            }
            String action = request.args().path("action").asString("");
            log.info("[Tool][browser] execute conversationUid={} messageUid={} stepUid={} action={}",
                    request.conversationUid(), request.messageUid(), request.stepUid(), action);
            return executeWithRecovery(request, profileKey, start);
        } catch (Exception ex) {
            String errorMessage = buildErrorMessage(ex);
            log.warn("[Tool][browser] failed stepUid={} err={}", request.stepUid(), errorMessage, ex);
            return ToolResult.failure("BROWSER_ERROR", errorMessage, metric(start));
        }
    }

    private ToolResult executeWithRecovery(ToolRequest request, String profileKey, long start) {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                BrowserMode initialMode = selectInitialMode(request);
                BrowserContext context = resolveContext(profileKey, request, initialMode);
                Page page = currentPage(request.conversationUid(), context);
                page.setDefaultTimeout(request.timeoutMs());
                return executeAction(request, page, start, profileKey, initialMode);
            } catch (Exception ex) {
                last = ex;
                if (!isTargetClosed(ex) || attempt == 1) {
                    break;
                }
                log.info("[Tool][browser] target closed detected, rebuilding context/profile={} conversationUid={}",
                        profileKey, request.conversationUid());
                invalidateProfileContext(profileKey);
                pageByConversation.remove(request.conversationUid());
            }
        }
        throw new IllegalStateException(last == null ? "browser action failed" : last.getMessage(), last);
    }

    private ToolResult executeAction(ToolRequest request,
                                     Page page,
                                     long start,
                                     String profileKey,
                                     BrowserMode initialMode) throws Exception {
        String action = request.args().path("action").asString("");
        return switch (action) {
            case "open" -> {
                String url = request.args().path("url").asString("");
                yield navigateWithModeSelection(request, profileKey, page, start, initialMode, url, "opened ");
            }
            case "navigate" -> {
                String url = request.args().path("url").asString("");
                yield navigateWithModeSelection(request, profileKey, page, start, initialMode, url, "navigated ");
            }
            case "navigate_back" -> {
                page.goBack();
                yield ToolResult.success("navigated back", textArtifacts("url", page.url()), metric(start));
            }
            case "click" -> {
                String selector = request.args().path("selector").asString("");
                double clickTimeoutMs = Math.max(1_000L, agentProperties.getBrowser().getClickTimeoutSeconds() * 1000L);
                Locator target = resolveVisibleFirstLocator(page, selector);
                target.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(clickTimeoutMs));
                target.scrollIntoViewIfNeeded(new Locator.ScrollIntoViewIfNeededOptions().setTimeout(clickTimeoutMs));
                boolean forceFallbackUsed = false;
                try {
                    target.click(new Locator.ClickOptions().setTimeout(clickTimeoutMs));
                } catch (Exception clickEx) {
                    if (!agentProperties.getBrowser().isForceClickFallbackEnabled()) {
                        throw clickEx;
                    }
                    target.click(new Locator.ClickOptions().setTimeout(clickTimeoutMs).setForce(true));
                    forceFallbackUsed = true;
                }
                ObjectNode artifacts = textArtifacts("selector", selector);
                artifacts.put("forceFallbackUsed", forceFallbackUsed);
                yield ToolResult.success("clicked " + selector, artifacts, metric(start));
            }
            case "type" -> {
                String selector = request.args().path("selector").asString("");
                String text = request.args().path("text").asString("");
                page.locator(selector).first().fill(text);
                yield ToolResult.success("typed into " + selector, textArtifacts("selector", selector), metric(start));
            }
            case "extract_text" -> {
                String selector = request.args().path("selector").asString("body");
                String text = page.locator(selector).first().innerText();
                ObjectNode artifacts = textArtifacts("selector", selector);
                artifacts.put("length", text.length());
                yield ToolResult.success(text, artifacts, metric(start));
            }
            case "screenshot" -> {
                String output = request.args().path("output").asString("");
                Path outputPath = output == null || output.isBlank()
                        ? request.tmpDirectory().resolve(UuidUtil.newUuid() + ".png").toAbsolutePath().normalize()
                        : PathResolver.resolveInAgentWorkspace(output, request);
                java.nio.file.Files.createDirectories(outputPath.getParent());
                page.screenshot(new Page.ScreenshotOptions().setPath(outputPath));
                yield ToolResult.success("screenshot saved", textArtifacts("path", outputPath.toString()), metric(start));
            }
            case "download" -> {
                String selector = request.args().path("selector").asString("");
                String output = request.args().path("output").asString("");
                Download download = page.waitForDownload(() -> page.locator(selector).first().click());
                Path targetPath = resolveDownloadPath(request, download, output);
                Files.createDirectories(targetPath.getParent());
                download.saveAs(targetPath);
                ObjectNode artifacts = textArtifacts("path", targetPath.toString());
                artifacts.put("fileName", download.suggestedFilename());
                artifacts.put("selector", selector);
                yield ToolResult.success("download saved", artifacts, metric(start));
            }
            case "snapshot" -> {
                String html = page.content();
                ObjectNode artifacts = textArtifacts("url", page.url());
                artifacts.put("title", page.title());
                yield ToolResult.success(ToolTextUtils.truncateHead(html), artifacts, metric(start));
            }
            case "wait_for" -> {
                String selector = request.args().path("selector").asString("");
                String normalized = selector == null ? "" : selector.trim();
                if (normalized.startsWith("--load ")) {
                    String stateValue = normalized.substring("--load ".length()).trim().toLowerCase(Locale.ROOT);
                    LoadState loadState = parseLoadState(stateValue);
                    page.waitForLoadState(loadState);
                    ObjectNode artifacts = textArtifacts("loadState", stateValue);
                    yield ToolResult.success("waited for load state " + stateValue, artifacts, metric(start));
                }
                page.locator(selector).first().waitFor();
                yield ToolResult.success("waited for " + selector, textArtifacts("selector", selector), metric(start));
            }
            case "press_key" -> {
                String key = request.args().path("key").asString("");
                page.keyboard().press(key);
                yield ToolResult.success("pressed key " + key, textArtifacts("key", key), metric(start));
            }
            case "close" -> {
                page.close();
                pageByConversation.remove(request.conversationUid());
                yield ToolResult.success("page closed", textArtifacts("conversationUid", request.conversationUid()), metric(start));
            }
            default -> ToolResult.failure("INVALID_ACTION", "unsupported browser action: " + action, metric(start));
        };
    }

    private ToolResult navigateWithModeSelection(ToolRequest request,
                                                 String profileKey,
                                                 Page managedPage,
                                                 long start,
                                                 BrowserMode initialMode,
                                                 String url,
                                                 String successPrefix) {
        BrowserMode selectedMode = initialMode;
        boolean fallback = false;
        String switchReason = "initial";
        try {
            if (selectedMode == BrowserMode.LOCAL_BRIDGE) {
                Page page = ensurePageForMode(request, profileKey, selectedMode);
                page.navigate(url);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            } else {
                managedPage.navigate(url);
                managedPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            }
        } catch (Exception ex) {
            if (selectedMode != BrowserMode.LOCAL_BRIDGE || !agentProperties.getBrowser().getLocalBridge().isFallbackToManaged()) {
                throw ex;
            }
            Page fallbackPage = ensurePageForMode(request, profileKey, BrowserMode.MANAGED);
            fallbackPage.navigate(url);
            fallbackPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            fallback = true;
            selectedMode = BrowserMode.MANAGED;
            switchReason = "local_bridge_unavailable";
            log.warn("[Tool][browser] local bridge unavailable, fallback to managed err={}", buildErrorMessage(ex));
        }
        modeByConversation.put(request.conversationUid(), selectedMode);
        ObjectNode artifacts = textArtifacts("url", url);
        artifacts.put("selectedMode", selectedMode.value);
        artifacts.put("switchReason", switchReason);
        artifacts.put("fallback", fallback);
        return ToolResult.success(successPrefix + url, artifacts, metric(start));
    }

    @PreDestroy
    public void closeAll() {
        for (Page page : pageByConversation.values()) {
            try {
                page.close();
            } catch (Exception ignored) {
            }
        }
        pageByConversation.clear();
        profileByConversation.clear();
        modeByConversation.clear();

        for (BrowserContext context : contextByProfile.values()) {
            try {
                context.close();
            } catch (Exception ignored) {
            }
        }
        contextByProfile.clear();
        for (Browser browser : browserByProfile.values()) {
            try {
                browser.close();
            } catch (Exception ignored) {
            }
        }
        browserByProfile.clear();
        for (Process process : processByProfile.values()) {
            safelyDestroyProcess(process);
        }
        processByProfile.clear();

        if (playwright != null) {
            playwright.close();
        }
    }

    private BrowserContext resolveContext(String profileKey, ToolRequest request, BrowserMode mode) {
        String modeProfileKey = modeProfileKey(profileKey, mode);
        synchronized (contextByProfile) {
            BrowserContext existing = contextByProfile.get(modeProfileKey);
            if (isContextUsable(existing)) {
                return existing;
            }
            if (existing != null) {
                safelyCloseContext(existing);
                contextByProfile.remove(modeProfileKey);
                resetPagesForProfile(profileKey);
            }
            BrowserContext created = createContext(profileKey, request, mode);
            contextByProfile.put(modeProfileKey, created);
            return created;
        }
    }

    /**
     * Create or repair a persistent Chromium context for the given profile.
     *
     * <p>Key behavior:
     * - If local cache does not contain a platform-appropriate Chromium executable,
     * report download progress and install chromium.
     * - If launch fails with "Executable doesn't exist", force reinstall chromium and retry once.
     *
     * <p>This guards against partial cache states like:
     * old chromium revisions still present, but the executable is missing or only a partial
     * download was written to disk.
     *
     * <p>Progress notes:
     * {@code downloadedBytes} is measured from the actual browser cache directory on disk.
     * {@code estimatedTotalBytes} is only a UI estimate used to derive an approximate
     * percent; it is not the real download size reported by Playwright.
     */
    private synchronized BrowserContext createContext(String profileKey, ToolRequest request, BrowserMode mode) {
        if (mode == BrowserMode.LOCAL_BRIDGE) {
            return createLocalBridgeContext(profileKey, request);
        }
        configurePlaywrightDriverTmpDirectory();
        Path cacheRoot = resolvePlaywrightCacheRoot();
        Map<String, String> playwrightEnv = buildPlaywrightEnv(cacheRoot);
        long baselineBytes = cacheRoot == null ? 0L : safeDirectorySize(cacheRoot);
        long monitorStartedAt = System.currentTimeMillis();
        boolean maybeNeedDownload = !hasInstalledChromiumExecutable(cacheRoot);
        AtomicInteger cliProgressPercent = new AtomicInteger(-1);
        InstallAttemptState installProgressState = new InstallAttemptState();
        if (maybeNeedDownload) {
            request.reportProgress(
                    "browser.runtime.preparing",
                    "browser.runtime.checking_dependencies",
                    progressMetrics("checking", baselineBytes, baselineBytes, monitorStartedAt, cliProgressPercent.get(), installProgressState.snapshot())
            );
        }
        DownloadMonitor monitor = maybeNeedDownload
                ? startDownloadMonitor(request, cacheRoot, baselineBytes, monitorStartedAt, cliProgressPercent, installProgressState)
                : null;
        try {
            if (playwright == null) {
                ensureChromiumInstalled(request, cacheRoot, playwrightEnv, cliProgressPercent, installProgressState, monitorStartedAt);
                try {
                    playwright = Playwright.create(new Playwright.CreateOptions().setEnv(playwrightEnv));
                } catch (Exception ex) {
                    throw new IllegalStateException(
                            "failed to create playwright driver"
                            + " env.PLAYWRIGHT_BROWSERS_PATH=" + playwrightEnv.get("PLAYWRIGHT_BROWSERS_PATH")
                            + " env.HOME=" + playwrightEnv.getOrDefault("HOME", "")
                            + " env.TMPDIR=" + playwrightEnv.getOrDefault("TMPDIR", "")
                            + " cause=" + buildErrorMessage(ex),
                            ex
                    );
                }
            }
            boolean headless = agentProperties.getBrowser().isHeadless();
            Path userDataDir = profileDirectory(profileKey);
            Path executablePath = requireInstalledChromiumExecutable(cacheRoot);
            try {
                Files.createDirectories(userDataDir);
            } catch (Exception ex) {
                throw new IllegalStateException("failed to create browser profile dir: " + userDataDir, ex);
            }
            BrowserContext context;
            try {
                context = createBrowserContext(cacheRoot, playwrightEnv, profileKey, headless, userDataDir, executablePath);
            } catch (Exception launchEx) {
                if (!isMissingExecutable(launchEx)) {
                    throw launchEx;
                }
                log.info("[Tool][browser] chromium executable missing during launch, reinstalling runtime");
                long repairBaselineBytes = cacheRoot == null ? 0L : safeDirectorySize(cacheRoot);
                long repairStartedAt = System.currentTimeMillis();
                AtomicInteger repairCliProgressPercent = new AtomicInteger(-1);
                InstallAttemptState repairInstallState = new InstallAttemptState();
                request.reportProgress(
                        "browser.runtime.preparing",
                        "browser.runtime.checking_dependencies",
                        progressMetrics("checking", repairBaselineBytes, 0L, repairStartedAt, repairCliProgressPercent.get(), repairInstallState.snapshot())
                );
                DownloadMonitor repairMonitor = startDownloadMonitor(request, cacheRoot, repairBaselineBytes, repairStartedAt, repairCliProgressPercent, repairInstallState);
                try {
                    forceInstallChromium(request, cacheRoot, playwrightEnv, repairCliProgressPercent, repairInstallState, repairStartedAt);
                    executablePath = requireInstalledChromiumExecutable(cacheRoot);
                    context = createBrowserContext(cacheRoot, playwrightEnv, profileKey, headless, userDataDir, executablePath);
                    long repairFinalBytes = cacheRoot == null ? repairBaselineBytes : safeDirectorySize(cacheRoot);
                    long repairDownloadedBytes = Math.max(0L, repairFinalBytes - repairBaselineBytes);
                    String repairDetails = repairDownloadedBytes > 0
                            ? "browser.runtime.ready_with_cache_delta:" + formatBytes(repairDownloadedBytes)
                            : "browser.runtime.ready";
                    request.reportProgress(
                            "browser.runtime.ready",
                            repairDetails,
                            progressMetrics("ready", repairFinalBytes, repairDownloadedBytes, repairStartedAt, repairCliProgressPercent.get(), repairInstallState.snapshot())
                    );
                } finally {
                    stopDownloadMonitor(repairMonitor);
                }
            }
            long finalBytes = cacheRoot == null ? baselineBytes : safeDirectorySize(cacheRoot);
            long downloadedBytes = Math.max(0L, finalBytes - baselineBytes);
            String details = downloadedBytes > 0
                    ? "browser.runtime.ready_with_cache_delta:" + formatBytes(downloadedBytes)
                    : "browser.runtime.ready";
            if (maybeNeedDownload) {
                request.reportProgress(
                        "browser.runtime.ready",
                        details,
                        progressMetrics("ready", finalBytes, downloadedBytes, monitorStartedAt, cliProgressPercent.get(), installProgressState.snapshot())
                );
            }
            log.info("[Tool][browser] browser context created profile={} dir={} headless={} mode={}",
                    profileKey, userDataDir, headless, PlatformSupport.isWindows() ? "windows_cdp" : "persistent");
            return context;
        } finally {
            stopDownloadMonitor(monitor);
        }
    }

    private synchronized BrowserContext createLocalBridgeContext(String profileKey, ToolRequest request) {
        String endpoint = normalizeCdpEndpoint(agentProperties.getBrowser().getLocalBridge().getCdpEndpoint());
        ensureLoopbackEndpoint(endpoint);
        ensureLocalBridgeEndpointReady(endpoint);
        if (playwright == null) {
            Path cacheRoot = resolvePlaywrightCacheRoot();
            Map<String, String> playwrightEnv = buildPlaywrightEnv(cacheRoot);
            playwright = Playwright.create(new Playwright.CreateOptions().setEnv(playwrightEnv));
        }
        Browser browser = playwright.chromium().connectOverCDP(
                endpoint,
                new BrowserType.ConnectOverCDPOptions()
                        .setIsLocal(true)
                        .setTimeout((double) Math.max(1, agentProperties.getBrowser().getLocalBridge().getConnectTimeoutMs()))
        );
        BrowserContext context = resolveConnectedContext(browser);
        String key = modeProfileKey(profileKey, BrowserMode.LOCAL_BRIDGE);
        Browser staleBrowser = browserByProfile.put(key, browser);
        if (staleBrowser != null && staleBrowser != browser) {
            try {
                staleBrowser.close();
            } catch (Exception ignored) {
            }
        }
        safelyDestroyProcess(processByProfile.remove(key));
        log.info("[Tool][browser] local bridge connected profile={} endpoint={}", profileKey, endpoint);
        return context;
    }

    private void ensureLocalBridgeEndpointReady(String endpoint) {
        String probeUrl = normalizeDevToolsVersionUrl(endpoint);
        if (probeUrl.isBlank()) {
            return;
        }
        if (isDevToolsEndpointReady(endpoint)) {
            return;
        }
        launchLocalChromeForCdp(endpoint);
        long timeoutMs = Math.max(3_000L, agentProperties.getBrowser().getLocalBridge().getConnectTimeoutMs());
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isDevToolsEndpointReady(endpoint)) {
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting local bridge endpoint ready", ex);
            }
        }
        throw new IllegalStateException("local bridge endpoint not ready: " + endpoint);
    }

    private boolean isDevToolsEndpointReady(String endpoint) {
        List<String> probeUrls = devToolsProbeUrls(endpoint);
        if (probeUrls.isEmpty()) {
            return true;
        }
        for (String probeUrl : probeUrls) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(probeUrl).openConnection();
                connection.setConnectTimeout(Math.max(500, agentProperties.getBrowser().getLocalBridge().getConnectTimeoutMs() / 2));
                connection.setReadTimeout(Math.max(500, agentProperties.getBrowser().getLocalBridge().getConnectTimeoutMs() / 2));
                connection.setRequestMethod("GET");
                int status = connection.getResponseCode();
                if (status != 200) {
                    continue;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                    if (body.toString().contains("webSocketDebuggerUrl")) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return false;
    }

    private void launchLocalChromeForCdp(String endpoint) {
        int port = parseEndpointPort(endpoint);
        String launcherKey = "local_bridge_launcher::" + port;
        Process existing = processByProfile.get(launcherKey);
        if (existing != null && existing.isAlive()) {
            return;
        }
        if (!PlatformSupport.isMac()) {
            throw new IllegalStateException("local bridge auto-start is currently supported on macOS only");
        }
        Path chromeExecutable = Path.of("/Applications", "Google Chrome.app", "Contents", "MacOS", "Google Chrome");
        if (!Files.isRegularFile(chromeExecutable)) {
            throw new IllegalStateException(
                    "google chrome is not installed or executable missing: " + chromeExecutable
                    + " please install Google Chrome from https://www.google.com/chrome/"
            );
        }
        Path userDataDir = NomoClawPaths.ensureRuntimeBrowserProfilesRoot()
                .resolve("local-bridge-cdp")
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(userDataDir);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create local bridge profile dir: " + userDataDir, ex);
        }
        List<String> command = new ArrayList<>();
        command.add(chromeExecutable.toString());
        command.add("--remote-debugging-address=127.0.0.1");
        command.add("--remote-debugging-port=" + port);
        command.add("--user-data-dir=" + userDataDir);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.redirectOutput(Redirect.DISCARD);
        try {
            Process process = pb.start();
            Process stale = processByProfile.put(launcherKey, process);
            if (stale != null && stale != process) {
                safelyDestroyProcess(stale);
            }
            log.info("[Tool][browser] started local bridge chrome launcher endpoint={} pid={}", endpoint, process.pid());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to auto-start local chrome for endpoint: " + endpoint, ex);
        }
    }

    private String normalizeDevToolsVersionUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(endpoint.trim());
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "";
            }
            int port = uri.getPort();
            if (port <= 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            return scheme + "://" + uri.getHost() + ":" + port + "/json/version";
        } catch (Exception ignored) {
            return "";
        }
    }

    private int parseEndpointPort(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if (uri.getPort() > 0) {
                return uri.getPort();
            }
            return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        } catch (Exception ex) {
            return 9222;
        }
    }

    private List<String> devToolsProbeUrls(String endpoint) {
        String primary = normalizeDevToolsVersionUrl(endpoint);
        if (primary.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        urls.add(primary);
        try {
            URI uri = URI.create(endpoint.trim());
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return List.copyOf(urls);
            }
            int port = uri.getPort();
            if (port <= 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            urls.add(scheme + "://localhost:" + port + "/json/version");
            urls.add(scheme + "://127.0.0.1:" + port + "/json/version");
            urls.add(scheme + "://[::1]:" + port + "/json/version");
        } catch (Exception ignored) {
        }
        return List.copyOf(urls);
    }

    private synchronized void ensureChromiumInstalled(ToolRequest request,
                                                      Path cacheRoot,
                                                      Map<String, String> playwrightEnv,
                                                      AtomicInteger cliProgressPercent,
                                                      InstallAttemptState installProgressState,
                                                      long startedAtMs) {
        if (chromiumInstallEnsured) {
            return;
        }
        if (hasInstalledChromiumExecutable(cacheRoot)) {
            chromiumInstallEnsured = true;
            return;
        }
        try {
            forceInstallChromium(request, cacheRoot, playwrightEnv, cliProgressPercent, installProgressState, startedAtMs);
            requireInstalledChromiumExecutable(cacheRoot);
            chromiumInstallEnsured = true;
        } catch (Exception ex) {
            log.warn("[Tool][browser] chromium install verification failed, retrying after cleanup cacheRoot={} err={}",
                    cacheRoot, ex.getMessage());
            try {
                cleanupBrokenChromiumInstall(cacheRoot);
                forceInstallChromium(request, cacheRoot, playwrightEnv, cliProgressPercent, installProgressState, startedAtMs);
                requireInstalledChromiumExecutable(cacheRoot);
                chromiumInstallEnsured = true;
            } catch (Exception retryEx) {
                throw new IllegalStateException("failed to install chromium runtime", retryEx);
            }
        }
    }

    private synchronized void forceInstallChromium(ToolRequest request,
                                                   Path cacheRoot,
                                                   Map<String, String> playwrightEnv,
                                                   AtomicInteger cliProgressPercent,
                                                   InstallAttemptState installProgressState,
                                                   long startedAtMs) {
        try {
            if (cliProgressPercent != null) {
                cliProgressPercent.set(-1);
            }
            if (installProgressState != null) {
                installProgressState.beginAttempt();
            }
            request.reportProgress(
                    "browser.runtime.installing_chromium",
                    "browser.runtime.installing_chromium_only",
                    progressMetrics("checking", 0L, 0L, startedAtMs, -1, installProgressState == null ? InstallAttemptSnapshot.empty() : installProgressState.snapshot())
            );
            log.info("[Tool][browser] installing chromium runtime cacheRoot={} env.PLAYWRIGHT_BROWSERS_PATH={}",
                    cacheRoot, playwrightEnv.get("PLAYWRIGHT_BROWSERS_PATH"));
            runPlaywrightCli(
                    buildPlaywrightInstallEnv(playwrightEnv),
                    line -> handlePlaywrightInstallOutput(line, request, cacheRoot, cliProgressPercent, installProgressState, startedAtMs),
                    "install",
                    "chromium"
            );
        } catch (Exception ex) {
            throw new IllegalStateException("failed to install chromium runtime", ex);
        }
    }

    private BrowserType.LaunchPersistentContextOptions buildLaunchOptions(boolean headless,
                                                                          Map<String, String> playwrightEnv,
                                                                          Path executablePath) {
        return new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(headless)
                .setAcceptDownloads(true)
                .setEnv(playwrightEnv)
                .setExecutablePath(executablePath);
    }

    private BrowserContext createBrowserContext(Path cacheRoot,
                                                Map<String, String> playwrightEnv,
                                                String profileKey,
                                                boolean headless,
                                                Path userDataDir,
                                                Path executablePath) {
        if (PlatformSupport.isWindows()) {
            return createWindowsContextOverCdp(cacheRoot, playwrightEnv, profileKey, headless, userDataDir, executablePath);
        }
        try {
            BrowserContext context = playwright.chromium().launchPersistentContext(
                    userDataDir,
                    buildLaunchOptions(headless, playwrightEnv, executablePath)
            );
            String modeProfileKey = modeProfileKey(profileKey, BrowserMode.MANAGED);
            Browser staleBrowser = browserByProfile.remove(modeProfileKey);
            if (staleBrowser != null) {
                try {
                    staleBrowser.close();
                } catch (Exception ignored) {
                }
            }
            safelyDestroyProcess(processByProfile.remove(modeProfileKey));
            return context;
        } catch (Exception launchEx) {
            throw enrichLaunchFailure("persistent", launchEx, cacheRoot, userDataDir, executablePath, null);
        }
    }

    private BrowserContext createWindowsContextOverCdp(Path cacheRoot,
                                                       Map<String, String> playwrightEnv,
                                                       String profileKey,
                                                       boolean headless,
                                                       Path userDataDir,
                                                       Path executablePath) {
        int debugPort = reserveTcpPort();
        String endpoint = "http://127.0.0.1:" + debugPort;
        Process process = null;
        try {
            process = launchWindowsChromeProcess(executablePath, userDataDir, headless, debugPort, playwrightEnv);
            waitForDebugPort(process, debugPort, executablePath, userDataDir);
            log.info("[Tool][browser] connecting over CDP executable={} cacheRoot={} userDataDir={} debugPort={} headless={}",
                    executablePath, cacheRoot, userDataDir, debugPort, headless);
            Browser browser = playwright.chromium().connectOverCDP(
                    endpoint,
                    new BrowserType.ConnectOverCDPOptions()
                            .setIsLocal(true)
                            .setTimeout((double) WINDOWS_CDP_CONNECT_TIMEOUT_MS)
            );
            BrowserContext context = resolveConnectedContext(browser);
            String modeProfileKey = modeProfileKey(profileKey, BrowserMode.MANAGED);
            Browser staleBrowser = browserByProfile.put(modeProfileKey, browser);
            if (staleBrowser != null && staleBrowser != browser) {
                try {
                    staleBrowser.close();
                } catch (Exception ignored) {
                }
            }
            Process staleProcess = processByProfile.put(modeProfileKey, process);
            if (staleProcess != null && staleProcess != process) {
                safelyDestroyProcess(staleProcess);
            }
            return context;
        } catch (Exception ex) {
            safelyDestroyProcess(process);
            throw enrichLaunchFailure("windows_cdp", ex, cacheRoot, userDataDir, executablePath, debugPort);
        }
    }

    private Process launchWindowsChromeProcess(Path executablePath,
                                               Path userDataDir,
                                               boolean headless,
                                               int debugPort,
                                               Map<String, String> playwrightEnv) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(executablePath.toString());
        command.add("--no-sandbox");
        command.addAll(WINDOWS_CHROME_STABLE_ARGS);
        command.add("--remote-debugging-address=127.0.0.1");
        command.add("--remote-debugging-port=" + debugPort);
        command.add("--user-data-dir=" + userDataDir);
        if (headless) {
            command.add("--headless=new");
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        if (playwrightEnv != null && !playwrightEnv.isEmpty()) {
            pb.environment().putAll(playwrightEnv);
        }
        pb.redirectErrorStream(true);
        pb.redirectOutput(Redirect.DISCARD);
        log.info("[Tool][browser] launching chromium over CDP command={} executable={} userDataDir={} debugPort={}",
                command, executablePath, userDataDir, debugPort);
        return pb.start();
    }

    private void waitForDebugPort(Process process,
                                  int debugPort,
                                  Path executablePath,
                                  Path userDataDir) throws Exception {
        long deadline = System.currentTimeMillis() + WINDOWS_CDP_CONNECT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (process != null && !process.isAlive()) {
                throw new IllegalStateException("chrome process exited before CDP was ready"
                                                + " executable=" + executablePath
                                                + " userDataDir=" + userDataDir
                                                + " debugPort=" + debugPort
                                                + " exitCode=" + process.exitValue());
            }
            try (var socket = new java.net.Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", debugPort), (int) WINDOWS_CDP_CONNECT_RETRY_INTERVAL_MS);
                return;
            } catch (Exception ignored) {
            }
            Thread.sleep(WINDOWS_CDP_CONNECT_RETRY_INTERVAL_MS);
        }
        throw new IllegalStateException("timed out waiting for chrome debug port"
                                        + " executable=" + executablePath
                                        + " userDataDir=" + userDataDir
                                        + " debugPort=" + debugPort);
    }

    private BrowserContext resolveConnectedContext(Browser browser) {
        if (browser == null) {
            throw new IllegalStateException("CDP browser connection returned null");
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            List<BrowserContext> contexts = browser.contexts();
            if (!contexts.isEmpty()) {
                return contexts.getFirst();
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for browser context", ex);
            }
        }
        throw new IllegalStateException("no browser context available after CDP connection");
    }

    /**
     * Executes Playwright CLI in a child process to avoid {@code CLI.main(...)} calling
     * {@code System.exit(...)} and terminating the current backend JVM.
     *
     * <p>The provided environment must be the same one later passed to
     * {@link Playwright#create(Playwright.CreateOptions)} so install, launch, and progress
     * reporting all resolve the same browser cache directory.
     *
     * <p>Verified against Playwright Java 1.58.0:
     * {@code com.microsoft.playwright.CLI.main(...)} ends with {@code System.exit(exitCode)}.
     */
    private void runPlaywrightCli(Map<String, String> env, Consumer<String> stdoutLineConsumer, String... args) throws Exception {
        Class<?> driverClass = Class.forName("com.microsoft.playwright.impl.driver.Driver");
        Method ensureDriverInstalled = driverClass.getMethod("ensureDriverInstalled", Map.class, Boolean.class);
        Object driver = ensureDriverInstalled.invoke(null, Map.of(), Boolean.FALSE);
        Method createProcessBuilder = driverClass.getMethod("createProcessBuilder");
        ProcessBuilder pb = (ProcessBuilder) createProcessBuilder.invoke(driver);
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }
        pb.command().addAll(Arrays.asList(args));
        pb.redirectErrorStream(true);
        pb.redirectOutput(Redirect.PIPE);
        Process process = pb.start();
        Thread pipeThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stdoutLineConsumer != null) {
                        stdoutLineConsumer.accept(line);
                    }
                    log.info("[Tool][browser][playwright-cli] {}", line);
                }
            } catch (Exception ex) {
                log.warn("[Tool][browser] failed reading playwright cli output err={}", ex.getMessage());
            }
        }, "playwright-cli-output");
        pipeThread.setDaemon(true);
        pipeThread.start();
        int exitCode = process.waitFor();
        try {
            pipeThread.join(1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (exitCode != 0) {
            throw new IllegalStateException("playwright cli failed with exit code " + exitCode);
        }
    }

    /**
     * Returns whether the cache root already contains a Chromium revision with a platform-specific
     * browser executable on disk. This is stricter than checking for a {@code chromium-*}
     * directory alone and avoids treating partial downloads as complete installs.
     */
    private boolean hasInstalledChromiumExecutable(Path cacheRoot) {
        return resolveInstalledChromiumExecutable(cacheRoot) != null;
    }

    private Path requireInstalledChromiumExecutable(Path cacheRoot) {
        Path executable = resolveInstalledChromiumExecutable(cacheRoot);
        if (executable != null) {
            return executable;
        }
        throw new IllegalStateException("chromium executable not found under cache root: " + cacheRoot);
    }

    private Path resolveInstalledChromiumExecutable(Path cacheRoot) {
        if (cacheRoot == null || !Files.isDirectory(cacheRoot)) {
            return null;
        }
        try (var stream = Files.list(cacheRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        return name.startsWith("chromium-");
                    })
                    .map(this::resolveChromiumExecutable)
                    .filter(this::isUsableExecutable)
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path resolveChromiumExecutable(Path revisionDir) {
        if (revisionDir == null) {
            return null;
        }
        if (PlatformSupport.isWindows()) {
            List<Path> candidates = List.of(
                    revisionDir.resolve("chrome-win64").resolve("chrome.exe"),
                    revisionDir.resolve("chrome-win").resolve("chrome.exe")
            );
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            return null;
        }
        if (PlatformSupport.isMac()) {
            // Playwright 1.58+ chromium on macOS uses Chrome for Testing layout.
            // Keep legacy Chromium.app paths for backward compatibility.
            List<Path> candidates = List.of(
                    revisionDir.resolve("chrome-mac-arm64")
                            .resolve("Google Chrome for Testing.app")
                            .resolve("Contents")
                            .resolve("MacOS")
                            .resolve("Google Chrome for Testing"),
                    revisionDir.resolve("chrome-mac-x64")
                            .resolve("Google Chrome for Testing.app")
                            .resolve("Contents")
                            .resolve("MacOS")
                            .resolve("Google Chrome for Testing"),
                    revisionDir.resolve("chrome-mac")
                            .resolve("Google Chrome for Testing.app")
                            .resolve("Contents")
                            .resolve("MacOS")
                            .resolve("Google Chrome for Testing"),
                    revisionDir.resolve("chrome-mac")
                            .resolve("Chromium.app")
                            .resolve("Contents")
                            .resolve("MacOS")
                            .resolve("Chromium")
            );
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
            return null;
        }
        List<Path> candidates = List.of(
                revisionDir.resolve("chrome-linux").resolve("chrome"),
                revisionDir.resolve("chrome-linux64").resolve("chrome")
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isUsableExecutable(Path executable) {
        if (executable == null || !Files.isRegularFile(executable)) {
            return false;
        }
        return Files.isReadable(executable);
    }

    private IllegalStateException enrichLaunchFailure(String mode,
                                                      Exception launchEx,
                                                      Path cacheRoot,
                                                      Path userDataDir,
                                                      Path executablePath,
                                                      Integer debugPort) {
        StringBuilder message = new StringBuilder("failed to launch chromium");
        message.append(" mode=").append(mode);
        message.append(" executable=").append(executablePath);
        message.append(" cacheRoot=").append(cacheRoot);
        message.append(" userDataDir=").append(userDataDir);
        if (debugPort != null) {
            message.append(" debugPort=").append(debugPort);
        }
        String detail = buildErrorMessage(launchEx);
        if (!detail.isBlank()) {
            message.append(" cause=").append(detail);
        }
        if (isWindowsNativeBrowserCrash(launchEx)) {
            message.append(" diagnosis=windows_native_browser_crash");
            message.append(" likelyCause=profile_lock_or_permissions_or_corrupted_chromium_runtime");
        }
        return new IllegalStateException(message.toString(), launchEx);
    }

    private boolean isWindowsNativeBrowserCrash(Throwable ex) {
        if (!PlatformSupport.isWindows()) {
            return false;
        }
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                && (message.contains("CreateFile() Error: 5")
                    || message.contains("exitCode=3221226356")
                    || message.contains("0xc0000374")
                    || message.contains("STATUS_HEAP_CORRUPTION"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private DownloadMonitor startDownloadMonitor(ToolRequest request,
                                                 Path cacheRoot,
                                                 long baselineBytes,
                                                 long startedAtMs,
                                                 AtomicInteger cliProgressPercent,
                                                 InstallAttemptState installProgressState) {
        if (cacheRoot == null) {
            return null;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "playwright-download-progress");
            thread.setDaemon(true);
            return thread;
        });
        AtomicLong lastReportedBytes = new AtomicLong(baselineBytes);
        AtomicLong lastReportAt = new AtomicLong(System.currentTimeMillis());
        scheduler.scheduleAtFixedRate(() -> {
            long currentBytes = safeDirectorySize(cacheRoot);
            long downloadedBytes = Math.max(0L, currentBytes - baselineBytes);
            long lastBytes = lastReportedBytes.get();
            long now = System.currentTimeMillis();
            long bytesDelta = currentBytes - lastBytes;
            long elapsedSinceLastReport = now - lastReportAt.get();
            boolean shouldReportByDelta = bytesDelta >= DOWNLOAD_REPORT_MIN_DELTA_BYTES;
            boolean shouldForceHeartbeat = elapsedSinceLastReport >= DOWNLOAD_REPORT_FORCE_INTERVAL_MS;
            if (!shouldReportByDelta && !shouldForceHeartbeat) {
                return;
            }
            if (shouldReportByDelta && !lastReportedBytes.compareAndSet(lastBytes, currentBytes)) {
                return;
            }
            if (!shouldReportByDelta) {
                lastReportedBytes.set(currentBytes);
            }
            {
                lastReportAt.set(now);
                request.reportProgress(
                        "browser.runtime.downloading",
                        "browser.runtime.cached_bytes:" + formatBytes(downloadedBytes),
                        progressMetrics("downloading", currentBytes, downloadedBytes, startedAtMs,
                                cliProgressPercent == null ? -1 : cliProgressPercent.get(),
                                installProgressState == null ? InstallAttemptSnapshot.empty() : installProgressState.snapshot())
                );
            }
        }, 300L, DOWNLOAD_REPORT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return new DownloadMonitor(scheduler);
    }

    private void stopDownloadMonitor(DownloadMonitor monitor) {
        if (monitor == null) {
            return;
        }
        monitor.scheduler().shutdownNow();
    }

    private void handlePlaywrightInstallOutput(String line,
                                               ToolRequest request,
                                               Path cacheRoot,
                                               AtomicInteger cliProgressPercent,
                                               InstallAttemptState installProgressState,
                                               long startedAtMs) {
        if (line == null || line.isBlank()) {
            return;
        }
        String trimmed = line.trim();
        Matcher startMatcher = PLAYWRIGHT_DOWNLOAD_START_PATTERN.matcher(trimmed);
        if (startMatcher.find()) {
            if (installProgressState != null) {
                installProgressState.onDownloadStart(startMatcher.group(1));
            }
            if (cliProgressPercent != null) {
                cliProgressPercent.set(0);
            }
            long cacheBytes = cacheRoot == null ? 0L : safeDirectorySize(cacheRoot);
            request.reportProgress(
                    "browser.runtime.downloading",
                    "browser.runtime.downloading_artifact:" + startMatcher.group(1),
                    progressMetrics("downloading", cacheBytes, 0L, startedAtMs, 0,
                            installProgressState == null ? InstallAttemptSnapshot.empty() : installProgressState.snapshot())
            );
            return;
        }

        Matcher doneMatcher = PLAYWRIGHT_DOWNLOAD_DONE_PATTERN.matcher(trimmed);
        if (doneMatcher.find()) {
            if (installProgressState != null) {
                installProgressState.onDownloadDone(doneMatcher.group(1));
            }
            if (cliProgressPercent != null) {
                cliProgressPercent.set(100);
            }
            long cacheBytes = cacheRoot == null ? 0L : safeDirectorySize(cacheRoot);
            request.reportProgress(
                    "browser.runtime.downloading",
                    "browser.runtime.downloaded_artifact:" + doneMatcher.group(1),
                    progressMetrics("downloading", cacheBytes, 0L, startedAtMs, 100,
                            installProgressState == null ? InstallAttemptSnapshot.empty() : installProgressState.snapshot())
            );
            return;
        }

        Matcher matcher = PLAYWRIGHT_PROGRESS_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            return;
        }
        int percent = parseIntSafe(matcher.group(1), -1);
        if (percent < 0 || percent > 100) {
            return;
        }
        long artifactTotalBytes = -1L;
        Matcher sizedMatcher = PLAYWRIGHT_PROGRESS_WITH_SIZE_PATTERN.matcher(trimmed);
        if (sizedMatcher.find()) {
            artifactTotalBytes = parseSizeToBytes(sizedMatcher.group(2), sizedMatcher.group(3));
        }
        if (installProgressState != null) {
            installProgressState.onProgress(percent, artifactTotalBytes);
        }
        if (cliProgressPercent != null) {
            cliProgressPercent.set(percent);
        }
        long cacheBytes = cacheRoot == null ? 0L : safeDirectorySize(cacheRoot);
        request.reportProgress(
                "browser.runtime.downloading",
                "browser.runtime.downloading_cli:" + percent + "%",
                progressMetrics("downloading", cacheBytes, 0L, startedAtMs, percent,
                        installProgressState == null ? InstallAttemptSnapshot.empty() : installProgressState.snapshot())
        );
    }

    /**
     * Builds progress metrics for the UI.
     *
     * <p>{@code cacheBytes} and {@code downloadedBytes} are real filesystem measurements.
     * {@code estimatedTotalBytes} is intentionally heuristic, because Playwright does not
     * expose the browser archive's total size through this code path.
     */
    private JsonNode progressMetrics(String phase,
                                     long cacheBytes,
                                     long downloadedBytes,
                                     long startedAtMs,
                                     int cliPercent,
                                     InstallAttemptSnapshot installAttempt) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        metrics.put("phase", phase);
        metrics.put("cacheBytes", Math.max(0L, cacheBytes));
        metrics.put("downloadedBytes", Math.max(0L, downloadedBytes));
        metrics.put("elapsedMs", elapsedMs);
        metrics.put("cliProgressPercent", Math.max(-1, cliPercent));
        metrics.put("attemptId", installAttempt.attemptId());
        metrics.put("attemptIndex", installAttempt.attemptIndex());
        metrics.put("retryCount", installAttempt.retryCount());
        metrics.put("currentArtifact", installAttempt.currentArtifact());
        metrics.put("segmentPercent", installAttempt.segmentPercent());
        metrics.put("overallPercent", installAttempt.overallPercent());
        metrics.put("segmentBytesTotal", installAttempt.currentArtifactTotalBytes());
        tools.jackson.databind.node.ArrayNode completedNode = metrics.putArray("completedArtifacts");
        for (String artifact : installAttempt.completedArtifacts()) {
            completedNode.add(artifact);
        }
        long estimatedTotalBytes = Math.max(ESTIMATED_BROWSER_DOWNLOAD_BYTES, Math.max(0L, downloadedBytes));
        boolean indeterminate = "downloading".equals(phase)
                && downloadedBytes <= 0L
                && cliPercent < 0
                && installAttempt.segmentPercent() < 0;
        int progressPercent = estimateProgressPercent(phase, downloadedBytes, estimatedTotalBytes, elapsedMs, cliPercent, installAttempt.overallPercent());
        metrics.put("estimatedTotalBytes", estimatedTotalBytes);
        metrics.put("progressPercent", progressPercent);
        metrics.put("indeterminate", indeterminate);
        return metrics;
    }

    /**
     * Estimates a user-facing percentage from observed on-disk bytes plus elapsed time.
     *
     * <p>This value is not a protocol-level download percentage; it is only meant to keep
     * the UI responsive until the install either completes or fails.
     */
    private int estimateProgressPercent(String phase,
                                        long downloadedBytes,
                                        long estimatedTotalBytes,
                                        long elapsedMs,
                                        int cliPercent,
                                        int overallPercent) {
        if ("ready".equals(phase)) {
            return 100;
        }
        if ("checking".equals(phase)) {
            return 3;
        }
        if ("downloading".equals(phase) && overallPercent >= 0) {
            return Math.max(1, Math.min(99, overallPercent));
        }
        if ("downloading".equals(phase) && cliPercent >= 0) {
            return Math.max(1, Math.min(99, cliPercent));
        }
        int floorByTime = 5 + (int) Math.min(90, Math.max(0L, elapsedMs / 1500L));
        if (downloadedBytes <= 0L || estimatedTotalBytes <= 0L) {
            return Math.min(95, floorByTime);
        }
        double raw = (downloadedBytes * 100.0) / estimatedTotalBytes;
        int rounded = (int) Math.round(raw);
        int combined = Math.max(rounded, floorByTime);
        if (combined < 5) {
            return 5;
        }
        return Math.min(95, combined);
    }

    private int parseIntSafe(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long parseSizeToBytes(String value, String unit) {
        if (value == null || value.isBlank() || unit == null || unit.isBlank()) {
            return -1L;
        }
        double numeric;
        try {
            numeric = Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return -1L;
        }
        long multiplier = switch (unit.trim().toUpperCase(Locale.ROOT)) {
            case "B" -> 1L;
            case "KB" -> 1_000L;
            case "MB" -> 1_000_000L;
            case "GB" -> 1_000_000_000L;
            case "KIB" -> 1_024L;
            case "MIB" -> 1_048_576L;
            case "GIB" -> 1_073_741_824L;
            default -> -1L;
        };
        if (multiplier <= 0L) {
            return -1L;
        }
        return Math.max(0L, Math.round(numeric * multiplier));
    }

    private Path resolvePlaywrightCacheRoot() {
        String appScopedPath = System.getenv("NOMOCLAW_PLAYWRIGHT_BROWSERS_PATH");
        if (appScopedPath != null && !appScopedPath.isBlank()) {
            return Path.of(appScopedPath.trim());
        }
        String customPath = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if (customPath != null && !customPath.isBlank()) {
            if ("0".equals(customPath.trim())) {
                return null;
            }
            return Path.of(customPath.trim());
        }
        String userHome = System.getProperty("user.home", "");
        if (PlatformSupport.isMac() && !userHome.isBlank()) {
            Path sharedMacCache = Path.of(userHome, "Library", "Caches", "ms-playwright");
            if (Files.isDirectory(sharedMacCache)) {
                return sharedMacCache;
            }
        }
        return NomoClawPaths.ensureRuntimePlaywrightBrowsersRoot();
    }

    private Map<String, String> buildPlaywrightEnv(Path cacheRoot) {
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
        if (cacheRoot != null) {
            try {
                Files.createDirectories(cacheRoot);
                env.put("PLAYWRIGHT_BROWSERS_PATH", cacheRoot.toString());
            } catch (Exception ex) {
                log.warn("[Tool][browser] failed to prepare playwright cache root {}", cacheRoot, ex);
            }
        }
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            env.putIfAbsent("HOME", home);
        }
        Path tmpDir = NomoClawPaths.ensureRuntimeTmpRoot().resolve("playwright-driver").toAbsolutePath().normalize();
        try {
            Files.createDirectories(tmpDir);
            env.put("TMPDIR", tmpDir.toString());
        } catch (Exception ex) {
            log.warn("[Tool][browser] failed to prepare tmp dir {}", tmpDir, ex);
        }
        return env;
    }

    private Map<String, String> buildPlaywrightInstallEnv(Map<String, String> launchEnv) {
        Map<String, String> env = new HashMap<>(launchEnv == null ? Map.of() : launchEnv);
        // Installation must never inherit skip-download, otherwise Playwright "install" becomes a no-op.
        env.remove("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD");
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "0");
        return env;
    }

    private void cleanupBrokenChromiumInstall(Path cacheRoot) {
        if (cacheRoot == null || !Files.isDirectory(cacheRoot)) {
            return;
        }
        try (var stream = Files.list(cacheRoot)) {
            List<Path> staleRevisions = stream
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        return name.startsWith("chromium-");
                    })
                    .toList();
            for (Path revision : staleRevisions) {
                deleteRecursively(revision);
            }
            if (!staleRevisions.isEmpty()) {
                log.info("[Tool][browser] removed stale chromium revisions count={} cacheRoot={}", staleRevisions.size(), cacheRoot);
            }
        } catch (Exception ex) {
            log.warn("[Tool][browser] failed to cleanup stale chromium revisions cacheRoot={} err={}", cacheRoot, ex.getMessage());
        }
    }

    private void deleteRecursively(Path root) throws Exception {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void configurePlaywrightDriverTmpDirectory() {
        Path driverTmpDir = NomoClawPaths.ensureRuntimePluginsRoot()
                .resolve("browser")
                .resolve("lib")
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(driverTmpDir);
            cleanupStalePlaywrightDriverTempDirectories(driverTmpDir);
            System.setProperty("playwright.driver.tmpdir", driverTmpDir.toString());
        } catch (Exception ex) {
            log.warn("[Tool][browser] failed to prepare playwright driver temp dir {}", driverTmpDir, ex);
        }
    }

    private void cleanupStalePlaywrightDriverTempDirectories(Path driverTmpDir) {
        if (driverTmpDir == null || !Files.isDirectory(driverTmpDir)) {
            return;
        }
        Instant cutoff = Instant.now().minus(PLAYWRIGHT_DRIVER_TEMP_RETENTION);
        int removed = 0;
        try (var stream = Files.list(driverTmpDir)) {
            List<Path> staleDirs = stream
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        return name.startsWith(PLAYWRIGHT_DRIVER_TEMP_PREFIX);
                    })
                    .filter(path -> isOlderThan(path, cutoff))
                    .toList();
            for (Path staleDir : staleDirs) {
                deleteRecursively(staleDir);
                removed++;
            }
            if (removed > 0) {
                log.info("[Tool][browser] removed stale playwright driver temp dirs count={} root={}", removed, driverTmpDir);
            }
        } catch (Exception ex) {
            log.warn("[Tool][browser] failed to cleanup playwright driver temp dirs root={} err={}", driverTmpDir, ex.getMessage());
        }
    }

    private boolean isOlderThan(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (Exception ex) {
            return false;
        }
    }

    private long safeDirectorySize(Path root) {
        if (root == null || !Files.exists(root)) {
            return 0L;
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (Exception ignored) {
                    return 0L;
                }
            }).sum();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "0 B";
        }
        double value = bytes;
        String[] units = new String[]{"B", "KiB", "MiB", "GiB"};
        int idx = 0;
        while (value >= 1024.0 && idx < units.length - 1) {
            value /= 1024.0;
            idx++;
        }
        if (idx == 0) {
            return (long) value + " " + units[idx];
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[idx]);
    }

    private record DownloadMonitor(ScheduledExecutorService scheduler) {
    }

    private record InstallAttemptSnapshot(
            String attemptId,
            int attemptIndex,
            int retryCount,
            String currentArtifact,
            int segmentPercent,
            int overallPercent,
            long currentArtifactTotalBytes,
            List<String> completedArtifacts
    ) {
        private static InstallAttemptSnapshot empty() {
            return new InstallAttemptSnapshot("", 0, 0, "", -1, -1, 0L, List.of());
        }
    }

    private enum ArtifactStatus {
        PENDING,
        DOWNLOADING,
        DONE
    }

    private static final class InstallArtifactState {
        private final String name;
        private ArtifactStatus status;
        private int segmentPercent;
        private long totalBytes;
        private long estimatedBytes;

        private InstallArtifactState(String name, long estimatedBytes) {
            this.name = name;
            this.status = ArtifactStatus.PENDING;
            this.segmentPercent = -1;
            this.totalBytes = 0L;
            this.estimatedBytes = Math.max(0L, estimatedBytes);
        }

        private long weightBytes() {
            if (totalBytes > 0L) {
                return totalBytes;
            }
            if (estimatedBytes > 0L) {
                return estimatedBytes;
            }
            return PLAYWRIGHT_UNKNOWN_ARTIFACT_ESTIMATED_BYTES;
        }
    }

    private static final class InstallAttemptState {
        private String attemptId = "";
        private int attemptIndex = 0;
        private int maxOverallPercent = -1;
        private String currentArtifact = "";
        private int segmentPercent = -1;
        private long currentArtifactTotalBytes = 0L;
        private final LinkedHashMap<String, InstallArtifactState> artifacts = new LinkedHashMap<>();

        private synchronized void beginAttempt() {
            attemptIndex += 1;
            attemptId = UuidUtil.newUuid();
            maxOverallPercent = -1;
            currentArtifact = "";
            segmentPercent = -1;
            currentArtifactTotalBytes = 0L;
            artifacts.clear();
            for (String artifact : PLAYWRIGHT_DEFAULT_ARTIFACTS) {
                ensureArtifact(artifact);
            }
        }

        private synchronized void onDownloadStart(String rawArtifactLabel) {
            String artifactName = normalizeArtifactName(rawArtifactLabel);
            if (artifactName.isBlank()) {
                return;
            }
            InstallArtifactState artifact = ensureArtifact(artifactName);
            artifact.status = ArtifactStatus.DOWNLOADING;
            artifact.segmentPercent = 0;
            currentArtifact = artifactName;
            segmentPercent = 0;
            currentArtifactTotalBytes = artifact.weightBytes();
        }

        private synchronized void onProgress(int percent, long totalBytes) {
            if (currentArtifact.isBlank()) {
                return;
            }
            InstallArtifactState artifact = ensureArtifact(currentArtifact);
            if (artifact.status == ArtifactStatus.PENDING) {
                artifact.status = ArtifactStatus.DOWNLOADING;
            }
            if (percent >= 0) {
                int bounded = Math.max(0, Math.min(100, percent));
                segmentPercent = bounded;
                artifact.segmentPercent = bounded;
            }
            if (totalBytes > 0L) {
                artifact.totalBytes = totalBytes;
                artifact.estimatedBytes = Math.max(artifact.estimatedBytes, totalBytes);
            }
            currentArtifactTotalBytes = artifact.weightBytes();
        }

        private synchronized void onDownloadDone(String rawArtifactLabel) {
            String artifactName = normalizeArtifactName(rawArtifactLabel);
            if (artifactName.isBlank()) {
                artifactName = currentArtifact;
            }
            if (artifactName.isBlank()) {
                return;
            }
            InstallArtifactState artifact = ensureArtifact(artifactName);
            artifact.status = ArtifactStatus.DONE;
            artifact.segmentPercent = 100;
            if (artifactName.equals(currentArtifact)) {
                segmentPercent = 100;
                currentArtifactTotalBytes = artifact.weightBytes();
            }
        }

        private synchronized InstallAttemptSnapshot snapshot() {
            if (attemptIndex <= 0) {
                return InstallAttemptSnapshot.empty();
            }

            long totalWeight = 0L;
            long weightedDone = 0L;
            List<String> completed = new ArrayList<>();

            for (InstallArtifactState artifact : artifacts.values()) {
                long weight = artifact.weightBytes();
                totalWeight += weight;
                if (artifact.status == ArtifactStatus.DONE) {
                    weightedDone += weight;
                    completed.add(artifact.name);
                }
            }

            if (!currentArtifact.isBlank()) {
                InstallArtifactState current = artifacts.get(currentArtifact);
                if (current != null && current.status != ArtifactStatus.DONE && segmentPercent >= 0) {
                    long weight = current.weightBytes();
                    weightedDone += Math.round(weight * (Math.min(100, segmentPercent) / 100.0));
                }
            }

            int overallRaw = -1;
            if (totalWeight > 0L) {
                overallRaw = (int) Math.round((weightedDone * 100.0) / totalWeight);
                overallRaw = Math.max(0, Math.min(100, overallRaw));
            } else if (segmentPercent >= 0) {
                overallRaw = Math.max(0, Math.min(100, segmentPercent));
            }

            if (overallRaw >= 0) {
                maxOverallPercent = Math.max(maxOverallPercent, overallRaw);
            }

            int overallDisplay = maxOverallPercent;
            if (overallDisplay > 99) {
                overallDisplay = 99;
            }

            long currentWeight = 0L;
            if (!currentArtifact.isBlank()) {
                InstallArtifactState current = artifacts.get(currentArtifact);
                if (current != null) {
                    currentWeight = current.weightBytes();
                }
            }
            if (currentWeight <= 0L) {
                currentWeight = Math.max(0L, currentArtifactTotalBytes);
            }

            return new InstallAttemptSnapshot(
                    attemptId,
                    attemptIndex,
                    Math.max(0, attemptIndex - 1),
                    currentArtifact,
                    segmentPercent,
                    overallDisplay,
                    currentWeight,
                    List.copyOf(completed)
            );
        }

        private InstallArtifactState ensureArtifact(String artifactName) {
            String normalized = normalizeArtifactName(artifactName);
            if (normalized.isBlank()) {
                normalized = artifactName == null ? "" : artifactName.trim().toLowerCase(Locale.ROOT);
            }
            if (normalized.isBlank()) {
                normalized = "unknown_artifact";
            }
            return artifacts.computeIfAbsent(
                    normalized,
                    key -> new InstallArtifactState(key, PLAYWRIGHT_ARTIFACT_ESTIMATED_BYTES.getOrDefault(key, PLAYWRIGHT_UNKNOWN_ARTIFACT_ESTIMATED_BYTES))
            );
        }

        private String normalizeArtifactName(String raw) {
            if (raw == null) {
                return "";
            }
            String normalized = raw.trim();
            if (normalized.isBlank()) {
                return "";
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lower.contains("chrome for testing")) {
                return "chromium";
            }
            if (lower.contains("chrome headless shell")) {
                return "chromium_headless_shell";
            }
            if (lower.contains("ffmpeg")) {
                return "ffmpeg";
            }
            return normalized.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase(Locale.ROOT);
        }
    }

    private String buildErrorMessage(Throwable ex) {
        if (ex == null) {
            return "unknown error";
        }
        String message = ex.getMessage();
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootMessage = root.getMessage();
        if (rootMessage == null || rootMessage.isBlank()) {
            return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
        }
        if (message == null || message.isBlank()) {
            return root.getClass().getSimpleName() + ": " + rootMessage;
        }
        if (message.contains(rootMessage)) {
            return message;
        }
        return message + " | rootCause=" + root.getClass().getSimpleName() + ": " + rootMessage;
    }

    private Page currentPage(String conversationUid, BrowserContext context) {
        Page existing = pageByConversation.get(conversationUid);
        if (isPageUsable(existing)) {
            return existing;
        }
        pageByConversation.remove(conversationUid);
        Page reusable = findReusablePage(context);
        if (reusable != null) {
            pageByConversation.put(conversationUid, reusable);
            return reusable;
        }
        Page page = context.newPage();
        pageByConversation.put(conversationUid, page);
        return page;
    }

    private Page findReusablePage(BrowserContext context) {
        List<Page> pages = context.pages();
        for (int i = pages.size() - 1; i >= 0; i--) {
            Page candidate = pages.get(i);
            if (isPageUsable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isPageUsable(Page page) {
        if (page == null || page.isClosed()) {
            return false;
        }
        try {
            page.url();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isContextUsable(BrowserContext context) {
        if (context == null) {
            return false;
        }
        try {
            context.pages();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void safelyCloseContext(BrowserContext context) {
        try {
            context.close();
        } catch (Exception ignored) {
        }
    }

    private void resetPagesForProfile(String profileKey) {
        profileByConversation.forEach((conversationUid, mappedProfile) -> {
            if (!profileKey.equals(mappedProfile)) {
                return;
            }
            Page page = pageByConversation.remove(conversationUid);
            if (page != null) {
                try {
                    page.close();
                } catch (Exception ignored) {
                }
            }
            modeByConversation.remove(conversationUid);
        });
    }

    private void invalidateProfileContext(String profileKey) {
        synchronized (contextByProfile) {
            BrowserContext managed = contextByProfile.remove(modeProfileKey(profileKey, BrowserMode.MANAGED));
            if (managed != null) {
                safelyCloseContext(managed);
            }
            BrowserContext local = contextByProfile.remove(modeProfileKey(profileKey, BrowserMode.LOCAL_BRIDGE));
            if (local != null) {
                safelyCloseContext(local);
            }
        }
        Browser staleBrowser = browserByProfile.remove(modeProfileKey(profileKey, BrowserMode.MANAGED));
        if (staleBrowser != null) {
            try {
                staleBrowser.close();
            } catch (Exception ignored) {
            }
        }
        Browser localBridgeBrowser = browserByProfile.remove(modeProfileKey(profileKey, BrowserMode.LOCAL_BRIDGE));
        if (localBridgeBrowser != null) {
            try {
                localBridgeBrowser.close();
            } catch (Exception ignored) {
            }
        }
        safelyDestroyProcess(processByProfile.remove(modeProfileKey(profileKey, BrowserMode.MANAGED)));
        safelyDestroyProcess(processByProfile.remove(modeProfileKey(profileKey, BrowserMode.LOCAL_BRIDGE)));
        resetPagesForProfile(profileKey);
    }

    private int reserveTcpPort() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
            return serverSocket.getLocalPort();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to reserve local debug port", ex);
        }
    }

    private void safelyDestroyProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isTargetClosed(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("Target page, context or browser has been closed")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // Detects Playwright's canonical error text when required browser binary is absent.
    private boolean isMissingExecutable(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("Executable doesn't exist at")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ObjectNode metric(long start) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        metrics.put("durationMs", System.currentTimeMillis() - start);
        metrics.put("ts", Instant.now().toString());
        return metrics;
    }

    private ObjectNode textArtifacts(String key, String value) {
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put(key, value);
        return artifacts;
    }

    private Path resolveDownloadPath(ToolRequest request, Download download, String output) {
        String fileName = download.suggestedFilename() == null || download.suggestedFilename().isBlank()
                ? UuidUtil.newUuid() + ".bin"
                : download.suggestedFilename();
        if (output == null || output.isBlank()) {
            return request.tmpDirectory().resolve(fileName).toAbsolutePath().normalize();
        }
        Path resolved = PathResolver.resolveInAgentWorkspace(output, request);
        if (output.endsWith("/") || java.nio.file.Files.isDirectory(resolved) || !hasFileExtension(resolved)) {
            return resolved.resolve(fileName).toAbsolutePath().normalize();
        }
        return resolved;
    }

    private boolean hasFileExtension(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 && dotIndex < name.length() - 1;
    }

    private LoadState parseLoadState(String stateValue) {
        if (stateValue == null || stateValue.isBlank()) {
            throw new IllegalArgumentException("wait_for load state is required, expected one of: load, domcontentloaded, networkidle");
        }
        return switch (stateValue) {
            case "load" -> LoadState.LOAD;
            case "domcontentloaded" -> LoadState.DOMCONTENTLOADED;
            case "networkidle" -> LoadState.NETWORKIDLE;
            default -> throw new IllegalArgumentException(
                    "unsupported wait_for load state: " + stateValue + ", expected one of: load, domcontentloaded, networkidle"
            );
        };
    }

    private Locator resolveVisibleFirstLocator(Page page, String selector) {
        String normalized = selector == null ? "" : selector.trim();
        Locator base = page.locator(normalized).first();
        if (normalized.isBlank() || normalized.contains(":visible")) {
            return base;
        }
        try {
            Locator visible = page.locator(normalized + ":visible").first();
            if (visible.count() > 0) {
                return visible;
            }
        } catch (Exception ignored) {
        }
        return base;
    }

    private BrowserMode selectInitialMode(ToolRequest request) {
        String action = request.args().path("action").asString("").trim().toLowerCase(Locale.ROOT);
        BrowserMode configured = configuredMode();
        BrowserMode current = modeByConversation.get(request.conversationUid());
        if (current != null) {
            return current;
        }
        if (!"open".equals(action) && !"navigate".equals(action)) {
            return configured == BrowserMode.LOCAL_BRIDGE ? BrowserMode.LOCAL_BRIDGE : BrowserMode.MANAGED;
        }
        if (configured == BrowserMode.MANAGED) {
            return BrowserMode.MANAGED;
        }
        if (configured == BrowserMode.LOCAL_BRIDGE) {
            return BrowserMode.LOCAL_BRIDGE;
        }
        String host = extractHost(request.args().path("url").asString(""));
        if (matchesDomain(host, agentProperties.getBrowser().getLocalBridge().getLocalBridgeDomains())) {
            return BrowserMode.LOCAL_BRIDGE;
        }
        return BrowserMode.MANAGED;
    }

    private Page ensurePageForMode(ToolRequest request, String profileKey, BrowserMode mode) {
        if (modeByConversation.get(request.conversationUid()) != mode) {
            Page existing = pageByConversation.remove(request.conversationUid());
            if (existing != null) {
                try {
                    existing.close();
                } catch (Exception ignored) {
                }
            }
        }
        BrowserContext context = resolveContext(profileKey, request, mode);
        Page page = currentPage(request.conversationUid(), context);
        page.setDefaultTimeout(request.timeoutMs());
        return page;
    }

    private BrowserMode configuredMode() {
        String raw = agentProperties.getBrowser().getMode();
        if (raw == null || raw.isBlank()) {
            return BrowserMode.AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "managed" -> BrowserMode.MANAGED;
            case "local_bridge" -> BrowserMode.LOCAL_BRIDGE;
            default -> BrowserMode.AUTO;
        };
    }

    private String modeProfileKey(String profileKey, BrowserMode mode) {
        return mode.value + "::" + profileKey;
    }

    private String normalizeCdpEndpoint(String endpoint) {
        String normalized = endpoint == null ? "" : endpoint.trim();
        return normalized.isBlank() ? "http://localhost:9222" : normalized;
    }

    private void ensureLoopbackEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (Exception ex) {
            throw new IllegalStateException("invalid local bridge cdp endpoint: " + endpoint, ex);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().trim().toLowerCase(Locale.ROOT);
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host) && !"::1".equals(host)) {
            throw new IllegalStateException("local bridge endpoint must be loopback: " + endpoint);
        }
    }

    private String extractHost(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean matchesDomain(String host, List<String> patterns) {
        if (host == null || host.isBlank() || patterns == null || patterns.isEmpty()) {
            return false;
        }
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String normalizedPattern = pattern.trim().toLowerCase(Locale.ROOT);
            if (normalizedPattern.startsWith("*.")) {
                String suffix = normalizedPattern.substring(1);
                if (normalizedHost.endsWith(suffix)) {
                    return true;
                }
                continue;
            }
            if (normalizedHost.equals(normalizedPattern) || normalizedHost.endsWith("." + normalizedPattern)) {
                return true;
            }
        }
        return false;
    }

    private enum BrowserMode {
        MANAGED("managed"),
        LOCAL_BRIDGE("local_bridge"),
        AUTO("auto");

        private final String value;

        BrowserMode(String value) {
            this.value = value;
        }
    }

    private String resolveProfileKey(ToolRequest request) {
        if (agentProperties.getBrowser().isSharedProfileEnabled()) {
            String configured = agentProperties.getBrowser().getSharedProfileName();
            String shared = configured == null ? "" : configured.trim();
            if (!shared.isBlank()) {
                return sanitizeKey(shared);
            }
            return "shared";
        }
        String agentUid = request.agentUid() == null ? "" : request.agentUid().trim();
        if (!agentUid.isBlank()) {
            return sanitizeKey(agentUid);
        }
        String agentName = request.agentName() == null ? "" : request.agentName().trim();
        if (!agentName.isBlank()) {
            return sanitizeKey(agentName);
        }
        return "default";
    }

    private Path profileDirectory(String profileKey) {
        return NomoClawPaths.ensureRuntimeBrowserProfilesRoot()
                .resolve(profileKey)
                .toAbsolutePath()
                .normalize();
    }

    private String sanitizeKey(String value) {
        String normalized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (normalized.isBlank()) {
            return "default";
        }
        return normalized;
    }
}
