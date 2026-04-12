package ai.nomoclaw.bot.tool;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CLI;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import ai.nomoclaw.bot.config.AgentProperties;
import ai.nomoclaw.bot.model.ToolRequest;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.orchestrator.MessageCancellationRegistry;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class BrowserTool implements Tool {
    private static final long DOWNLOAD_REPORT_INTERVAL_MS = 800L;
    private static final long DOWNLOAD_REPORT_MIN_DELTA_BYTES = 256L * 1024L;
    private static final long DOWNLOAD_REPORT_FORCE_INTERVAL_MS = 2200L;
    private static final long ESTIMATED_BROWSER_DOWNLOAD_BYTES = 520L * 1024L * 1024L;

    private final Map<String, BrowserContext> contextByProfile = new ConcurrentHashMap<>();
    private final Map<String, String> profileByConversation = new ConcurrentHashMap<>();
    private final Map<String, Page> pageByConversation = new ConcurrentHashMap<>();
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
        return "browser_tool";
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
            String action = request.args().path("action").asText("");
            log.info("[Tool][browser] execute conversationUid={} messageUid={} stepUid={} action={}",
                    request.conversationUid(), request.messageUid(), request.stepUid(), action);
            return executeWithRecovery(request, profileKey, start);
        } catch (Exception ex) {
            log.warn("[Tool][browser] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
            return ToolResult.failure("BROWSER_ERROR", ex.getMessage(), metric(start));
        }
    }

    private ToolResult executeWithRecovery(ToolRequest request, String profileKey, long start) {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                BrowserContext context = resolveContext(profileKey, request);
                Page page = currentPage(request.conversationUid(), context);
                page.setDefaultTimeout(request.timeoutMs());
                return executeAction(request, page, start);
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

    private ToolResult executeAction(ToolRequest request, Page page, long start) throws Exception {
        String action = request.args().path("action").asText("");
        return switch (action) {
            case "open" -> {
                String url = request.args().path("url").asText("");
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                yield ToolResult.success("opened " + url, textArtifacts("url", url), metric(start));
            }
            case "navigate" -> {
                String url = request.args().path("url").asText("");
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                yield ToolResult.success("navigated " + url, textArtifacts("url", url), metric(start));
            }
            case "navigate_back" -> {
                page.goBack();
                yield ToolResult.success("navigated back", textArtifacts("url", page.url()), metric(start));
            }
            case "click" -> {
                String selector = request.args().path("selector").asText("");
                page.locator(selector).first().click();
                yield ToolResult.success("clicked " + selector, textArtifacts("selector", selector), metric(start));
            }
            case "type" -> {
                String selector = request.args().path("selector").asText("");
                String text = request.args().path("text").asText("");
                page.locator(selector).first().fill(text);
                yield ToolResult.success("typed into " + selector, textArtifacts("selector", selector), metric(start));
            }
            case "extract_text" -> {
                String selector = request.args().path("selector").asText("body");
                String text = page.locator(selector).first().innerText();
                ObjectNode artifacts = textArtifacts("selector", selector);
                artifacts.put("length", text.length());
                yield ToolResult.success(text, artifacts, metric(start));
            }
            case "screenshot" -> {
                String output = request.args().path("output").asText("");
                Path outputPath = output == null || output.isBlank()
                        ? request.tmpDirectory().resolve(UUID.randomUUID() + ".png").toAbsolutePath().normalize()
                        : PathResolver.resolveInAgentWorkspace(output, request);
                java.nio.file.Files.createDirectories(outputPath.getParent());
                page.screenshot(new Page.ScreenshotOptions().setPath(outputPath));
                yield ToolResult.success("screenshot saved", textArtifacts("path", outputPath.toString()), metric(start));
            }
            case "download" -> {
                String selector = request.args().path("selector").asText("");
                String output = request.args().path("output").asText("");
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
                String selector = request.args().path("selector").asText("");
                page.locator(selector).first().waitFor();
                yield ToolResult.success("waited for " + selector, textArtifacts("selector", selector), metric(start));
            }
            case "press_key" -> {
                String key = request.args().path("key").asText("");
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

        for (BrowserContext context : contextByProfile.values()) {
            try {
                context.close();
            } catch (Exception ignored) {
            }
        }
        contextByProfile.clear();

        if (playwright != null) {
            playwright.close();
        }
    }

    private BrowserContext resolveContext(String profileKey, ToolRequest request) {
        synchronized (contextByProfile) {
            BrowserContext existing = contextByProfile.get(profileKey);
            if (existing != null && isContextUsable(existing)) {
                return existing;
            }
            if (existing != null) {
                safelyCloseContext(existing);
                contextByProfile.remove(profileKey);
                resetPagesForProfile(profileKey);
            }
            BrowserContext created = createContext(profileKey, request);
            contextByProfile.put(profileKey, created);
            return created;
        }
    }

    /**
     * Create or repair a persistent Chromium context for the given profile.
     *
     * <p>Key behavior:
     * - If local cache looks missing, report download progress and install chromium.
     * - If launch fails with "Executable doesn't exist", force reinstall chromium and retry once.
     *
     * <p>This guards against partial cache states like:
     * old chromium revisions still present, but current required revision removed manually.
     */
    private synchronized BrowserContext createContext(String profileKey, ToolRequest request) {
        Path cacheRoot = resolvePlaywrightCacheRoot();
        long baselineBytes = cacheRoot == null ? 0L : safeDirectorySize(cacheRoot);
        long monitorStartedAt = System.currentTimeMillis();
        boolean maybeNeedDownload = !hasAnyChromiumCache(cacheRoot);
        if (maybeNeedDownload) {
            request.reportProgress(
                    "browser.runtime.preparing",
                    "browser.runtime.checking_dependencies",
                    progressMetrics("checking", baselineBytes, baselineBytes, monitorStartedAt)
            );
        }
        DownloadMonitor monitor = maybeNeedDownload
                ? startDownloadMonitor(request, cacheRoot, baselineBytes, monitorStartedAt)
                : null;
        try {
            if (playwright == null) {
                ensureChromiumInstalled(request);
                Map<String, String> env = new HashMap<>(System.getenv());
                env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
                playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env));
            }
            boolean headless = agentProperties.getBrowser().isHeadless();
            Path userDataDir = profileDirectory(profileKey);
            try {
                Files.createDirectories(userDataDir);
            } catch (Exception ex) {
                throw new IllegalStateException("failed to create browser profile dir: " + userDataDir, ex);
            }
            BrowserContext context;
            try {
                context = playwright.chromium().launchPersistentContext(
                        userDataDir,
                        new BrowserType.LaunchPersistentContextOptions()
                                .setHeadless(headless)
                                .setAcceptDownloads(true)
                );
            } catch (Exception launchEx) {
                if (!isMissingExecutable(launchEx)) {
                    throw launchEx;
                }
                // Required chromium revision is missing/corrupted: reinstall runtime and retry launch.
                log.info("[Tool][browser] chromium executable missing, reinstalling runtime");
                long repairBaselineBytes = cacheRoot == null ? 0L : safeDirectorySize(cacheRoot);
                long repairStartedAt = System.currentTimeMillis();
                request.reportProgress(
                        "browser.runtime.preparing",
                        "browser.runtime.checking_dependencies",
                        progressMetrics("checking", repairBaselineBytes, 0L, repairStartedAt)
                );
                DownloadMonitor repairMonitor = startDownloadMonitor(request, cacheRoot, repairBaselineBytes, repairStartedAt);
                try {
                    forceInstallChromium(request);
                    context = playwright.chromium().launchPersistentContext(
                            userDataDir,
                            new BrowserType.LaunchPersistentContextOptions()
                                    .setHeadless(headless)
                                    .setAcceptDownloads(true)
                    );
                    long repairFinalBytes = cacheRoot == null ? repairBaselineBytes : safeDirectorySize(cacheRoot);
                    long repairDownloadedBytes = Math.max(0L, repairFinalBytes - repairBaselineBytes);
                    String repairDetails = repairDownloadedBytes > 0
                            ? "browser.runtime.ready_with_cache_delta:" + formatBytes(repairDownloadedBytes)
                            : "browser.runtime.ready";
                    request.reportProgress(
                            "browser.runtime.ready",
                            repairDetails,
                            progressMetrics("ready", repairFinalBytes, repairDownloadedBytes, repairStartedAt)
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
                        progressMetrics("ready", finalBytes, downloadedBytes, monitorStartedAt)
                );
            }
            log.info("[Tool][browser] persistent context created profile={} dir={} headless={}",
                    profileKey, userDataDir, headless);
            return context;
        } finally {
            stopDownloadMonitor(monitor);
        }
    }

    private synchronized void ensureChromiumInstalled(ToolRequest request) {
        if (chromiumInstallEnsured) {
            return;
        }
        Path cacheRoot = resolvePlaywrightCacheRoot();
        if (hasAnyChromiumCache(cacheRoot)) {
            chromiumInstallEnsured = true;
            return;
        }
        try {
            forceInstallChromium(request);
            chromiumInstallEnsured = true;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to install chromium runtime", ex);
        }
    }

    private synchronized void forceInstallChromium(ToolRequest request) {
        try {
            request.reportProgress(
                    "browser.runtime.installing_chromium",
                    "browser.runtime.installing_chromium_only",
                    progressMetrics("checking", 0L, 0L, System.currentTimeMillis())
            );
            CLI.main(new String[]{"install", "chromium"});
        } catch (Exception ex) {
            throw new IllegalStateException("failed to install chromium runtime", ex);
        }
    }

    private boolean hasAnyChromiumCache(Path cacheRoot) {
        if (cacheRoot == null || !Files.isDirectory(cacheRoot)) {
            return false;
        }
        try (var stream = Files.list(cacheRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName() == null ? "" : path.getFileName().toString())
                    .anyMatch(name -> name.startsWith("chromium-"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private DownloadMonitor startDownloadMonitor(ToolRequest request, Path cacheRoot, long baselineBytes, long startedAtMs) {
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
                        progressMetrics("downloading", currentBytes, downloadedBytes, startedAtMs)
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

    private JsonNode progressMetrics(String phase, long cacheBytes, long downloadedBytes, long startedAtMs) {
        ObjectNode metrics = JsonNodeFactory.instance.objectNode();
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - startedAtMs);
        metrics.put("phase", phase);
        metrics.put("cacheBytes", Math.max(0L, cacheBytes));
        metrics.put("downloadedBytes", Math.max(0L, downloadedBytes));
        metrics.put("elapsedMs", elapsedMs);
        long estimatedTotalBytes = Math.max(ESTIMATED_BROWSER_DOWNLOAD_BYTES, Math.max(0L, downloadedBytes));
        int progressPercent = estimateProgressPercent(phase, downloadedBytes, estimatedTotalBytes, elapsedMs);
        metrics.put("estimatedTotalBytes", estimatedTotalBytes);
        metrics.put("progressPercent", progressPercent);
        return metrics;
    }

    private int estimateProgressPercent(String phase, long downloadedBytes, long estimatedTotalBytes, long elapsedMs) {
        if ("ready".equals(phase)) {
            return 100;
        }
        if ("checking".equals(phase)) {
            return 3;
        }
        int floorByTime = 5 + (int) Math.min(35, Math.max(0L, elapsedMs / 1200L));
        if (downloadedBytes <= 0L || estimatedTotalBytes <= 0L) {
            return Math.min(60, floorByTime);
        }
        double raw = (downloadedBytes * 100.0) / estimatedTotalBytes;
        int rounded = (int) Math.round(raw);
        int combined = Math.max(rounded, floorByTime);
        if (combined < 5) {
            return 5;
        }
        return Math.min(95, combined);
    }

    private Path resolvePlaywrightCacheRoot() {
        String customPath = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if (customPath != null && !customPath.isBlank()) {
            if ("0".equals(customPath.trim())) {
                return null;
            }
            return Path.of(customPath.trim());
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", "");
        if (userHome.isBlank()) {
            return null;
        }
        if (osName.contains("mac")) {
            return Path.of(userHome, "Library", "Caches", "ms-playwright");
        }
        if (osName.contains("win")) {
            return Path.of(userHome, "AppData", "Local", "ms-playwright");
        }
        return Path.of(userHome, ".cache", "ms-playwright");
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

    private Page currentPage(String conversationUid, BrowserContext context) {
        Page existing = pageByConversation.get(conversationUid);
        if (isPageUsable(existing)) {
            return existing;
        }
        pageByConversation.remove(conversationUid);
        Page page = context.newPage();
        pageByConversation.put(conversationUid, page);
        return page;
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
        });
    }

    private void invalidateProfileContext(String profileKey) {
        synchronized (contextByProfile) {
            BrowserContext stale = contextByProfile.remove(profileKey);
            if (stale != null) {
                safelyCloseContext(stale);
            }
        }
        resetPagesForProfile(profileKey);
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
                ? UUID.randomUUID() + ".bin"
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

    private String resolveProfileKey(ToolRequest request) {
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
        return NomoClawPaths.root()
                .resolve("browser-profiles")
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
