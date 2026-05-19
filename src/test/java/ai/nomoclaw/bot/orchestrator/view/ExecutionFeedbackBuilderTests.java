package ai.nomoclaw.bot.orchestrator.view;

import ai.nomoclaw.bot.config.I18nConfig;
import ai.nomoclaw.bot.model.ApprovalStatus;
import ai.nomoclaw.bot.model.PlanStep;
import ai.nomoclaw.bot.model.RiskLevel;
import ai.nomoclaw.bot.model.StepStatus;
import ai.nomoclaw.bot.model.ToolResult;
import ai.nomoclaw.bot.util.LocalizedMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionFeedbackBuilderTests {

    private final MessageSource messageSource = new I18nConfig().messageSource();
    private final LocalizedMessages localizedMessages = new LocalizedMessages(messageSource);
    private final ExecutionFeedbackBuilder feedbackBuilder = new ExecutionFeedbackBuilder(localizedMessages);

    @AfterEach
    void cleanupLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void shouldRenderAllWebSearchResultsInSuccessDetails() {
        LocaleContextHolder.setLocale(Locale.US);
        PlanStep step = webSearchStep();
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("endpoint", "https://www.bing.com/search?q=test&setlang=zh-Hans");
        ArrayNode results = JsonNodeFactory.instance.arrayNode();
        results.add(resultItem("OpenAI", "https://openai.com"));
        results.add(resultItem("GitHub", "https://github.com"));
        artifacts.set("results", results);

        String details = feedbackBuilder.buildStepSuccessDetails(step, ToolResult.success("", artifacts, JsonNodeFactory.instance.objectNode()));

        assertTrue(details.contains("Web search completed with 2 result(s)."));
        assertTrue(details.contains("Search URL: https://www.bing.com/search?q=test&setlang=zh-Hans"));
        assertTrue(details.contains("1. OpenAI"));
        assertTrue(details.contains("https://openai.com"));
        assertTrue(details.contains("2. GitHub"));
        assertTrue(details.contains("https://github.com"));
    }

    @Test
    void shouldFallbackToDefaultWhenWebSearchResultsEmpty() {
        LocaleContextHolder.setLocale(Locale.US);
        PlanStep step = webSearchStep();
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("endpoint", "https://www.bing.com/search?q=test&setlang=zh-Hans");
        artifacts.set("results", JsonNodeFactory.instance.arrayNode());

        String details = feedbackBuilder.buildStepSuccessDetails(step, ToolResult.success("", artifacts, JsonNodeFactory.instance.objectNode()));

        assertEquals("Web search completed. Search URL: https://www.bing.com/search?q=test&setlang=zh-Hans", details);
    }

    @Test
    void shouldSkipMissingUrlAndUseUntitledFallbackForMissingTitle() {
        LocaleContextHolder.setLocale(Locale.US);
        PlanStep step = webSearchStep();
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("endpoint", "https://www.bing.com/search?q=test&setlang=zh-Hans");
        ArrayNode results = JsonNodeFactory.instance.arrayNode();
        results.add(resultItem("", "https://example.com/no-title"));
        results.add(resultItem("Missing URL", ""));
        artifacts.set("results", results);

        String details = feedbackBuilder.buildStepSuccessDetails(step, ToolResult.success("", artifacts, JsonNodeFactory.instance.objectNode()));

        assertTrue(details.contains("Web search completed with 1 result(s)."));
        assertTrue(details.contains("Search URL: https://www.bing.com/search?q=test&setlang=zh-Hans"));
        assertTrue(details.contains("1. (untitled)"));
        assertTrue(details.contains("https://example.com/no-title"));
        assertTrue(!details.contains("Missing URL"));
    }

    @Test
    void shouldRenderWebFetchTitleAndUrlInSuccessDetails() {
        LocaleContextHolder.setLocale(Locale.US);
        PlanStep step = webFetchStep("https://example.com/page");
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("title", "Example Domain");
        artifacts.put("url", "https://example.com/page");

        String details = feedbackBuilder.buildStepSuccessDetails(step, ToolResult.success("", artifacts, JsonNodeFactory.instance.objectNode()));

        assertTrue(details.contains("Web fetch complete: https://example.com/page"));
        assertTrue(details.contains("Example Domain"));
        assertTrue(details.contains("https://example.com/page"));
    }

    @Test
    void shouldFallbackWebFetchTitleWhenMissing() {
        LocaleContextHolder.setLocale(Locale.US);
        PlanStep step = webFetchStep("https://example.com/page");
        ObjectNode artifacts = JsonNodeFactory.instance.objectNode();
        artifacts.put("url", "https://example.com/page");

        String details = feedbackBuilder.buildStepSuccessDetails(step, ToolResult.success("", artifacts, JsonNodeFactory.instance.objectNode()));

        assertTrue(details.contains("Web fetch complete: https://example.com/page"));
        assertTrue(details.contains("(untitled)"));
        assertTrue(details.contains("https://example.com/page"));
    }

    @Test
    void shouldKeepWebSearchFailureEndpointDetails() {
        LocaleContextHolder.setLocale(Locale.US);
        PlanStep step = webSearchStep();
        String longFailure = "all search endpoints failed: "
                + "https://a.example.com/search?q=test -> timeout; "
                + "https://b.example.com/search?q=test -> http 503; "
                + "https://c.example.com/search?q=test -> connection reset";

        String details = feedbackBuilder.buildStepFailureDetails(
                step,
                new ToolResult(false, null, null, "WEB_SEARCH_ERROR", longFailure, JsonNodeFactory.instance.objectNode())
        );

        assertTrue(details.contains("https://a.example.com/search?q=test"));
        assertTrue(details.contains("https://b.example.com/search?q=test"));
        assertTrue(details.contains("https://c.example.com/search?q=test"));
    }

    private PlanStep webSearchStep() {
        return new PlanStep(
                "step-web-search",
                1,
                1,
                "Web search",
                "WebSearchTool",
                JsonNodeFactory.instance.objectNode().put("query", "test"),
                RiskLevel.LOW,
                "",
                StepStatus.CREATED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
    }

    private PlanStep webFetchStep(String url) {
        return new PlanStep(
                "step-web-fetch",
                1,
                1,
                "Web fetch",
                "WebFetchTool",
                JsonNodeFactory.instance.objectNode().put("url", url),
                RiskLevel.LOW,
                "",
                StepStatus.CREATED,
                0,
                null,
                null,
                ApprovalStatus.NONE
        );
    }

    private ObjectNode resultItem(String title, String url) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("title", title);
        node.put("url", url);
        return node;
    }
}
