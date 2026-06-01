package ai.nomoclaw.bot.orchestrator.view;

import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.util.LocalizedMessages;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Builds user-facing step text and event payload fields.
 *
 * <p>This class must keep wire-field names and i18n keys stable because frontend
 * rendering depends on them.
 */
@Component
public class ExecutionFeedbackBuilder {

    private final LocalizedMessages localizedMessages;

    public ExecutionFeedbackBuilder(LocalizedMessages localizedMessages) {
        this.localizedMessages = localizedMessages;
    }

    public String summaryRejected() {
        return i18n("agent.step.summary.rejected");
    }

    public String summaryRunning() {
        return i18n("agent.step.summary.running");
    }

    public String summaryWaitingApproval() {
        return i18n("agent.step.summary.waitingApproval");
    }

    public String summaryPlanned() {
        return i18n("agent.step.summary.planned");
    }

    public String summaryCompleted() {
        return i18n("agent.step.summary.completed");
    }

    public String summaryFailed() {
        return i18n("agent.step.summary.failed");
    }

    public String detailsRejected() {
        return i18n("agent.step.details.rejected");
    }

    public String defaultDisplayTitle() {
        return i18n("agent.step.display.default");
    }

    public String messageCanceled() {
        return i18n("agent.message.canceled");
    }

    public String messageFailedApprovalRejected() {
        return i18n("agent.message.failed.approvalRejected");
    }

    public String messageCompletedDefault() {
        return i18n("agent.message.completed");
    }

    public String messageFailedDefault() {
        return i18n("agent.message.failed");
    }

    public String buildStepTitle(String toolName, JsonNode toolArgs) {
        return switch (nullToEmpty(toolName)) {
            case "CommandTool" -> {
                String command = toolArgs.path("command").asString("");
                yield command.isBlank() ? i18n("agent.step.title.command") : i18n("agent.step.title.command.withArg", abbreviate(command, 96));
            }
            case "BrowserTool" -> {
                String action = toolArgs.path("action").asString("");
                String target = toolArgs.path("selector").asString("");
                if (target.isBlank()) {
                    target = toolArgs.path("url").asString("");
                }
                yield i18n(
                        target.isBlank() ? "agent.step.title.browser" : "agent.step.title.browser.withTarget",
                        action.isBlank() ? i18n("agent.step.title.browser.defaultAction") : action,
                        abbreviate(target, 48)
                );
            }
            case "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool" -> {
                String action = switch (nullToEmpty(toolName)) {
                    case "ReadFileTool" -> "read";
                    case "ListFileTool" -> "list";
                    case "CreateFileTool" -> toolArgs.path("mode").asString("create_or_truncate");
                    case "EditFileTool" -> "edit";
                    default -> "";
                };
                String path = toolArgs.path("path").asString("");
                yield i18n(
                        path.isBlank() ? "agent.step.title.file" : "agent.step.title.file.withPath",
                        action.isBlank() ? i18n("agent.step.title.file.defaultAction") : action,
                        abbreviate(path, 48)
                );
            }
            case "CronCreateTool" -> {
                String task = toolArgs.path("task").asString("");
                yield task.isBlank() ? i18n("agent.step.title.cron.create") : i18n("agent.step.title.cron.create.withTask", abbreviate(task, 48));
            }
            case "CronDeleteTool" -> {
                String jobUid = toolArgs.path("jobUid").asString("");
                yield jobUid.isBlank() ? i18n("agent.step.title.cron.delete") : i18n("agent.step.title.cron.delete.withId", abbreviate(jobUid, 48));
            }
            case "CronListTool" -> i18n("agent.step.title.cron.list");
            case "ImageLoaderTool" -> {
                String reference = toolArgs.path("reference").asString("");
                yield reference.isBlank() ? i18n("agent.step.title.image") : i18n("agent.step.title.image.withRef", abbreviate(reference, 48));
            }
            case "WebSearchTool" -> {
                String query = toolArgs.path("query").asString("");
                yield query.isBlank() ? i18n("agent.step.title.web.search") : i18n("agent.step.title.web.search.withQuery", abbreviate(query, 48));
            }
            case "WebFetchTool" -> {
                String url = toolArgs.path("url").asString("");
                yield url.isBlank() ? i18n("agent.step.title.web.fetch") : i18n("agent.step.title.web.fetch.withUrl", abbreviate(url, 48));
            }
            default -> i18n("agent.step.title.tool.default", nullToEmpty(toolName));
        };
    }

    public String buildDisplayTitle(PlanStep step) {
        JsonNode toolArgs = step.toolArgs();
        return switch (nullToEmpty(step.toolName())) {
            case "CommandTool" -> {
                String command = toolArgs.path("command").asString("");
                yield command.isBlank() ? i18n("agent.step.display.command.running") : i18n("agent.step.display.command.running.withCommand", command);
            }
            case "BrowserTool" -> switch (toolArgs.path("action").asString("")) {
                case "open", "navigate" -> i18n("agent.step.display.browser.open");
                case "click" -> i18n("agent.step.display.browser.click");
                case "type" -> i18n("agent.step.display.browser.type");
                case "extract_text" -> i18n("agent.step.display.browser.extractText");
                case "screenshot" -> i18n("agent.step.display.browser.screenshot");
                case "download" -> i18n("agent.step.display.browser.download");
                case "wait_for" -> i18n("agent.step.display.browser.waitFor");
                case "press_key" -> i18n("agent.step.display.browser.pressKey");
                case "snapshot" -> i18n("agent.step.display.browser.snapshot");
                default -> i18n("agent.step.display.browser.default");
            };
            case "ReadFileTool" -> i18n("agent.step.display.file.read");
            case "ListFileTool" -> i18n("agent.step.display.file.list");
            case "CreateFileTool" -> i18n("agent.step.display.file.create");
            case "EditFileTool" -> i18n("agent.step.display.file.edit");
            case "CronCreateTool" -> i18n("agent.step.display.cron.create");
            case "CronDeleteTool" -> i18n("agent.step.display.cron.delete");
            case "CronListTool" -> i18n("agent.step.display.cron.list");
            case "ImageLoaderTool" -> i18n("agent.step.display.image");
            case "DesktopScreenshotTool" -> i18n("agent.step.display.desktopScreenshot");
            case "FileSearchTool" -> i18n("agent.step.display.fileSearch");
            case "WebSearchTool" -> i18n("agent.step.display.web.search");
            case "WebFetchTool" -> i18n("agent.step.display.web.fetch");
            default -> step.title() == null || step.title().isBlank() ? i18n("agent.step.display.default") : step.title();
        };
    }

    public String buildStepPlanDetails(PlanStep step) {
        return switch (nullToEmpty(step.toolName())) {
            case "CommandTool" -> {
                String command = step.toolArgs().path("command").asString("");
                String cwd = step.toolArgs().path("cwd").asString("");
                yield command.isBlank()
                        ? i18n("agent.step.plan.command.planned")
                        : (cwd.isBlank()
                        ? i18n("agent.step.plan.command.withParam", command)
                        : i18n("agent.step.plan.command.withCommandAndCwd", command, cwd));
            }
            case "BrowserTool" -> {
                String action = step.toolArgs().path("action").asString("");
                String url = step.toolArgs().path("url").asString("");
                String selector = step.toolArgs().path("selector").asString("");
                String target = !url.isBlank() ? abbreviate(url, 72) : abbreviate(selector, 48);
                yield target.isBlank()
                        ? i18n("agent.step.plan.browser.planned")
                        : i18n("agent.step.plan.browser.withTarget",
                        action.isBlank() ? i18n("agent.step.title.browser.defaultAction") : action,
                        target);
            }
            case "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool" -> {
                String action = switch (nullToEmpty(step.toolName())) {
                    case "ReadFileTool" -> i18n("agent.step.plan.file.action.read");
                    case "ListFileTool" -> i18n("agent.step.plan.file.action.list");
                    case "CreateFileTool" -> {
                        String mode = step.toolArgs().path("mode").asString("create_or_truncate");
                        yield "append".equals(mode) ? i18n("agent.step.plan.file.action.append") : i18n("agent.step.plan.file.action.write");
                    }
                    case "EditFileTool" -> i18n("agent.step.plan.file.action.edit");
                    default -> i18n("agent.step.plan.file.action.default");
                };
                String path = step.toolArgs().path("path").asString("");
                yield path.isBlank()
                        ? i18n("agent.step.plan.file.planned")
                        : i18n("agent.step.plan.file.withPath", action.isBlank() ? i18n("agent.step.plan.file.action.default") : action, abbreviate(path, 72));
            }
            case "CronCreateTool" -> {
                String task = step.toolArgs().path("task").asString("");
                yield task.isBlank() ? i18n("agent.step.plan.cron.create") : i18n("agent.step.plan.cron.create.withTask", abbreviate(task, 72));
            }
            case "CronDeleteTool" -> {
                String jobUid = step.toolArgs().path("jobUid").asString("");
                yield jobUid.isBlank() ? i18n("agent.step.plan.cron.delete") : i18n("agent.step.plan.cron.delete.withId", abbreviate(jobUid, 72));
            }
            case "CronListTool" -> {
                String jobUid = step.toolArgs().path("jobUid").asString("");
                yield jobUid.isBlank() ? i18n("agent.step.plan.cron.list") : i18n("agent.step.plan.cron.list.withId", abbreviate(jobUid, 72));
            }
            case "ImageLoaderTool" -> {
                String reference = step.toolArgs().path("reference").asString("");
                yield reference.isBlank()
                        ? i18n("agent.step.plan.image")
                        : i18n("agent.step.plan.image.withRef", abbreviate(reference, 72));
            }
            case "WebSearchTool" -> {
                String query = step.toolArgs().path("query").asString("");
                yield query.isBlank()
                        ? i18n("agent.step.plan.web.search")
                        : i18n("agent.step.plan.web.search.withQuery", abbreviate(query, 72));
            }
            case "WebFetchTool" -> {
                String url = step.toolArgs().path("url").asString("");
                yield url.isBlank()
                        ? i18n("agent.step.plan.web.fetch")
                        : i18n("agent.step.plan.web.fetch.withUrl", abbreviate(url, 72));
            }
            default -> i18n("agent.step.plan.default");
        };
    }

    public String buildStepStartedDetails(PlanStep step) {
        return switch (nullToEmpty(step.toolName())) {
            case "CommandTool", "BrowserTool", "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool", "WebSearchTool", "WebFetchTool", "CronCreateTool", "CronDeleteTool", "CronListTool" -> buildStepPlanDetails(step)
                    .replace(i18n("agent.step.plan.systemPreparing"), i18n("agent.step.started.systemRunning"))
                    .replace(i18n("agent.step.plan.systemPlanned"), i18n("agent.step.started.systemRunning"));
            default -> i18n("agent.step.started.default");
        };
    }

    public String buildStepApprovalDetails(PlanStep step) {
        return i18n("agent.step.approval.prompt", formatApprovalAction(step.toolName(), step.toolArgs()));
    }

    public String buildStepSuccessDetails(PlanStep step, ToolResult result) {
        return switch (nullToEmpty(step.toolName())) {
            case "BrowserTool" -> {
                String path = result.artifacts() == null ? "" : result.artifacts().path("path").asString("");
                if (!path.isBlank()) {
                    yield i18n("agent.step.success.browser.saved", path);
                }
                yield hasMeaningfulText(result.output()) ? i18n("agent.step.success.browser.withOutput", abbreviate(result.output(), 120)) : i18n("agent.step.success.browser");
            }
            case "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool" -> {
                String path = result.artifacts() == null ? "" : result.artifacts().path("path").asString("");
                yield path.isBlank() ? i18n("agent.step.success.file") : i18n("agent.step.success.file.withPath", abbreviate(path, 96));
            }
            case "CommandTool" -> {
                String command = step.toolArgs().path("command").asString("");
                String stdout = result.artifacts() == null ? "" : result.artifacts().path("stdout").asString("");
                String stderr = result.artifacts() == null ? "" : result.artifacts().path("stderr").asString("");
                String outputBody = hasMeaningfulText(stdout) ? stdout : (hasMeaningfulText(result.output()) ? result.output() : stderr);
                String prefix = command.isBlank() ? i18n("agent.step.success.command") : i18n("agent.step.success.command.withParam", command);
                if (!hasMeaningfulText(outputBody)) {
                    yield prefix;
                }
                yield i18n("agent.step.success.command.withOutput", prefix, abbreviate(outputBody, 1200));
            }
            case "CronCreateTool" -> i18n("agent.step.success.cron.create");
            case "CronDeleteTool" -> i18n("agent.step.success.cron.delete");
            case "CronListTool" -> i18n("agent.step.success.cron.list");
            case "ImageLoaderTool" -> {
                int resolved = result.artifacts() == null ? 0 : result.artifacts().path("resolvedCount").asInt(0);
                boolean matched = result.artifacts() != null && result.artifacts().path("matched").asBoolean(false);
                if (!matched || resolved <= 0) {
                    yield i18n("agent.step.success.image.noMatch");
                }
                yield i18n("agent.step.success.image.withCount", resolved);
            }
            case "WebSearchTool" -> {
                String endpoint = result.artifacts() == null ? "" : result.artifacts().path("endpoint").asString("").trim();
                JsonNode results = result.artifacts() == null ? JsonNodeFactory.instance.arrayNode() : result.artifacts().path("results");
                if (!results.isArray() || results.isEmpty()) {
                    if (endpoint.isBlank()) {
                        yield i18n("agent.step.success.web.search");
                    }
                    yield i18n("agent.step.success.web.search.withEndpoint", endpoint);
                }
                StringBuilder details = new StringBuilder();
                int count = 0;
                for (JsonNode item : results) {
                    String url = item == null ? "" : item.path("url").asString("").trim();
                    if (url.isBlank()) {
                        continue;
                    }
                    String title = item.path("title").asString("").trim();
                    String renderedTitle = title.isBlank() ? i18n("agent.step.success.web.search.untitled") : title;
                    count++;
                    details.append(i18n("agent.step.success.web.search.item", count, renderedTitle, url)).append('\n');
                }
                if (count <= 0) {
                    if (endpoint.isBlank()) {
                        yield i18n("agent.step.success.web.search");
                    }
                    yield i18n("agent.step.success.web.search.withEndpoint", endpoint);
                }
                String body = details.toString().trim();
                if (endpoint.isBlank()) {
                    yield i18n("agent.step.success.web.search.withCount", count) + "\n" + body;
                }
                yield i18n("agent.step.success.web.search.withCountAndEndpoint", count, endpoint) + "\n" + body;
            }
            case "WebFetchTool" -> {
                String url = result.artifacts() == null ? "" : result.artifacts().path("url").asString("").trim();
                if (url.isBlank()) {
                    url = step.toolArgs().path("url").asString("").trim();
                }
                String title = result.artifacts() == null ? "" : result.artifacts().path("title").asString("").trim();
                String renderedTitle = title.isBlank() ? i18n("agent.step.success.web.fetch.untitled") : title;
                if (url.isBlank()) {
                    yield i18n("agent.step.success.web.fetch");
                }
                yield i18n("agent.step.success.web.fetch.withTitleAndUrl", url, renderedTitle);
            }
            default -> hasMeaningfulText(result.output()) ? abbreviate(result.output(), 140) : i18n("agent.step.success.default");
        };
    }

    public String buildStepFailureDetails(PlanStep step, ToolResult result) {
        String message = hasMeaningfulText(result.errorMessage()) ? result.errorMessage() : result.output();
        if (!hasMeaningfulText(message)) {
            message = i18n("agent.step.failure.default");
        }
        if ("WebSearchTool".equals(nullToEmpty(step.toolName()))) {
            return abbreviate(message, 1600);
        }
        if ("CommandTool".equals(nullToEmpty(step.toolName()))) {
            String command = step.toolArgs().path("command").asString("");
            String stderr = result.artifacts() == null ? "" : result.artifacts().path("stderr").asString("");
            String suffix = hasMeaningfulText(stderr) ? "\nstderr:\n" + abbreviate(stderr, 800) : "";
            if (!command.isBlank()) {
                return abbreviate(i18n("agent.step.failure.command", command, message) + suffix, 1600);
            }
        }
        return abbreviate(message, 180);
    }

    public void applyUserFacingFields(ObjectNode payload,
                                      PlanStep step,
                                      String status,
                                      String displaySummary,
                                      String displayDetails) {
        payload.put("status", status);
        payload.put("displayTitle", buildDisplayTitle(step));
        payload.put("displaySummary", nullToEmpty(displaySummary));
        payload.put("displayDetails", nullToEmpty(displayDetails));
    }

    public ObjectNode userFacingStepNode(PlanStep step,
                                         String status,
                                         String displaySummary,
                                         String displayDetails,
                                         Instant updatedTime) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("stepUid", step.stepUid());
        node.put("roundIndex", step.roundIndex());
        node.put("stepIndex", step.stepIndex());
        node.put("status", status);
        node.put("toolName", nullToEmpty(step.toolName()));
        node.set("toolArgs", step.toolArgs() == null ? JsonNodeFactory.instance.objectNode() : step.toolArgs());
        node.put("displayTitle", buildDisplayTitle(step));
        node.put("displaySummary", nullToEmpty(displaySummary));
        node.put("displayDetails", nullToEmpty(displayDetails));
        node.put("updatedTime", updatedTime == null ? "" : updatedTime.toString());
        return node;
    }

    public ObjectNode resultPayload(PlanStep step, ToolResult result, int attempt, int maxRounds) {
        // Keep this payload shape aligned with existing STEP_FINISHED / STEP_FAILED consumers.
        ObjectNode payload = stepPayload(step, result.success() ? "success" : "failed");
        payload.put("round", step.roundIndex());
        payload.put("maxRounds", maxRounds);
        payload.put("attempt", attempt);
        payload.put("success", result.success());
        payload.put("output", nullToEmpty(result.output()));
        payload.put("errorCode", nullToEmpty(result.errorCode()));
        payload.put("errorMessage", nullToEmpty(result.errorMessage()));
        payload.set("metrics", result.metrics() == null ? JsonNodeFactory.instance.objectNode() : result.metrics());
        payload.set("artifacts", result.artifacts() == null ? JsonNodeFactory.instance.objectNode() : result.artifacts());
        if (result.success()) {
            applyUserFacingFields(payload, step, "completed", summaryCompleted(), buildStepSuccessDetails(step, result));
        } else {
            applyUserFacingFields(payload, step, "failed", summaryFailed(), buildStepFailureDetails(step, result));
        }
        return payload;
    }

    public ObjectNode stepPayload(PlanStep step, String message) {
        // Base step payload reused by planned/running/completed/failed event nodes.
        ObjectNode payload = basePayload(message);
        payload.put("stepUid", step.stepUid());
        payload.put("roundIndex", step.roundIndex());
        payload.put("stepIndex", step.stepIndex());
        payload.put("title", step.title());
        payload.put("toolName", step.toolName());
        payload.set("toolArgs", step.toolArgs() == null ? JsonNodeFactory.instance.objectNode() : step.toolArgs());
        payload.put("riskLevel", step.riskLevel().name());
        return payload;
    }

    public ObjectNode basePayload(String message) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("message", nullToEmpty(message));
        return payload;
    }

    public String buildRunSummary(String status, int completedSteps, int totalSteps) {
        return switch (nullToEmpty(status)) {
            case "completed" -> i18n("agent.run.summary.completed", completedSteps, totalSteps);
            case "failed" -> i18n("agent.run.summary.failed", completedSteps, totalSteps);
            case "canceled" -> i18n("agent.run.summary.canceled", completedSteps, totalSteps);
            case "waiting_approval" -> i18n("agent.run.summary.waitingApproval", completedSteps, totalSteps);
            case "running" -> i18n("agent.run.summary.running", completedSteps, totalSteps);
            default -> i18n("agent.run.summary.planned", totalSteps);
        };
    }

    private String formatApprovalAction(String toolName, JsonNode toolArgs) {
        return switch (nullToEmpty(toolName)) {
            case "BrowserTool" -> {
                String action = toolArgs.path("action").asString("");
                String url = toolArgs.path("url").asString("");
                String selector = toolArgs.path("selector").asString("");
                String text = toolArgs.path("text").asString("");
                if ("open".equals(action) || "navigate".equals(action)) {
                    yield i18n("agent.step.approval.browser.open", url.isBlank() ? i18n("agent.step.approval.notProvided.url") : abbreviate(url, 96));
                }
                if ("click".equals(action)) {
                    yield i18n("agent.step.approval.browser.click", selector.isBlank() ? i18n("agent.step.approval.notProvided.target") : abbreviate(selector, 72));
                }
                if ("type".equals(action)) {
                    yield text.isBlank()
                            ? i18n("agent.step.approval.browser.type", selector.isBlank() ? i18n("agent.step.approval.notProvided.target") : abbreviate(selector, 72))
                            : i18n("agent.step.approval.browser.type.withText",
                            selector.isBlank() ? i18n("agent.step.approval.notProvided.target") : abbreviate(selector, 72),
                            abbreviate(text, 60));
                }
                if ("screenshot".equals(action)) {
                    String output = toolArgs.path("output").asString("");
                    yield output.isBlank()
                            ? i18n("agent.step.approval.browser.screenshot.default")
                            : i18n("agent.step.approval.browser.screenshot.output", abbreviate(output, 96));
                }
                yield i18n("agent.step.approval.browser.default");
            }
            case "CommandTool" -> {
                String command = toolArgs.path("command").asString("");
                String cwd = toolArgs.path("cwd").asString("");
                String renderedParam = command.isBlank() ? i18n("agent.step.approval.notProvided.command") : command;
                yield cwd.isBlank()
                        ? i18n("agent.step.approval.command", renderedParam)
                        : i18n("agent.step.approval.command.withCwd", renderedParam, cwd);
            }
            case "ReadFileTool", "ListFileTool", "CreateFileTool", "EditFileTool" -> {
                String action = switch (nullToEmpty(toolName)) {
                    case "ReadFileTool" -> i18n("agent.step.plan.file.action.read");
                    case "ListFileTool" -> i18n("agent.step.plan.file.action.list");
                    case "CreateFileTool" -> {
                        String mode = toolArgs.path("mode").asString("create_or_truncate");
                        yield "append".equals(mode) ? i18n("agent.step.plan.file.action.append") : i18n("agent.step.plan.file.action.write");
                    }
                    case "EditFileTool" -> i18n("agent.step.plan.file.action.edit");
                    default -> i18n("agent.step.plan.file.action.default");
                };
                String path = toolArgs.path("path").asString("");
                yield path.isBlank()
                        ? i18n("agent.step.approval.file", action)
                        : i18n("agent.step.approval.file.withPath", action, abbreviate(path, 96));
            }
            case "CronCreateTool" -> {
                String task = toolArgs.path("task").asString("");
                yield task.isBlank() ? i18n("agent.step.approval.cron.create") : i18n("agent.step.approval.cron.create.withTask", abbreviate(task, 72));
            }
            case "CronDeleteTool" -> {
                String jobUid = toolArgs.path("jobUid").asString("");
                yield jobUid.isBlank() ? i18n("agent.step.approval.cron.delete") : i18n("agent.step.approval.cron.delete.withId", abbreviate(jobUid, 72));
            }
            case "CronListTool" -> i18n("agent.step.approval.cron.list");
            case "WebSearchTool" -> {
                String query = toolArgs.path("query").asString("");
                yield query.isBlank() ? i18n("agent.step.approval.web.search") : i18n("agent.step.approval.web.search.withQuery", abbreviate(query, 72));
            }
            case "WebFetchTool" -> {
                String url = toolArgs.path("url").asString("");
                yield url.isBlank() ? i18n("agent.step.approval.web.fetch") : i18n("agent.step.approval.web.fetch.withUrl", abbreviate(url, 96));
            }
            default -> i18n("agent.step.approval.default");
        };
    }

    private String i18n(String code, Object... args) {
        return localizedMessages.get(code, args);
    }

    private boolean hasMeaningfulText(String text) {
        return text != null && !text.isBlank();
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String abbreviate(String text, int maxLength) {
        String normalized = nullToEmpty(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}
