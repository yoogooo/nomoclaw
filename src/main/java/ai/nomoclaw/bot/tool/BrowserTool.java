package ai.nomoclaw.bot.tool;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
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
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class BrowserTool implements Tool {

    private final Map<String, BrowserContext> contextByProfile = new ConcurrentHashMap<>();
    private final Map<String, String> profileByConversation = new ConcurrentHashMap<>();
    private final Map<String, Page> pageByConversation = new ConcurrentHashMap<>();
    private final AgentProperties agentProperties;
    private final MessageCancellationRegistry cancellationRegistry;
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
        try {
            if (cancellationRegistry.isCanceled(request.messageUid())) {
                return ToolResult.failure("CANCELLED", "message canceled", metric(start));
            }
            String profileKey = profileByConversation.computeIfAbsent(
                    request.conversationUid(),
                    ignored -> resolveProfileKey(request)
            );
            BrowserContext context = contextByProfile.computeIfAbsent(profileKey, this::createContext);
            Page page = currentPage(request.conversationUid(), context);
            page.setDefaultTimeout(request.timeoutMs());

            String action = request.args().path("action").asText("");
            log.info("[Tool][browser] execute conversationUid={} messageUid={} stepUid={} action={}",
                    request.conversationUid(), request.messageUid(), request.stepUid(), action);
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
        } catch (Exception ex) {
            log.warn("[Tool][browser] failed stepUid={} err={}", request.stepUid(), ex.getMessage());
            return ToolResult.failure("BROWSER_ERROR", ex.getMessage(), metric(start));
        }
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

    private synchronized BrowserContext createContext(String profileKey) {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        boolean headless = agentProperties.getBrowser().isHeadless();
        Path userDataDir = profileDirectory(profileKey);
        try {
            Files.createDirectories(userDataDir);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to create browser profile dir: " + userDataDir, ex);
        }
        BrowserContext context = playwright.chromium().launchPersistentContext(
                userDataDir,
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(headless)
                        .setAcceptDownloads(true)
        );
        log.info("[Tool][browser] persistent context created profile={} dir={} headless={}",
                profileKey, userDataDir, headless);
        return context;
    }

    private Page currentPage(String conversationUid, BrowserContext context) {
        Page existing = pageByConversation.get(conversationUid);
        if (existing != null && !existing.isClosed()) {
            return existing;
        }
        Page page = context.newPage();
        pageByConversation.put(conversationUid, page);
        return page;
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
