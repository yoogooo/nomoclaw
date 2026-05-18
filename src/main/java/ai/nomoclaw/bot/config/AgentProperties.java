package ai.nomoclaw.bot.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private final Command command = new Command();
    private final Browser browser = new Browser();
    private final Retry retry = new Retry();
    private final Loop loop = new Loop();
    private final Approval approval = new Approval();
    private final Api api = new Api();
    private final WebSearch webSearch = new WebSearch();

    public Command getParam() {
        return command;
    }

    public Browser getBrowser() {
        return browser;
    }

    public Retry getRetry() {
        return retry;
    }

    public Loop getLoop() {
        return loop;
    }

    public Approval getApproval() {
        return approval;
    }

    public Api getApi() {
        return api;
    }

    public WebSearch getWebSearch() {
        return webSearch;
    }

    public static class Command {
        @Min(1)
        private int timeoutSeconds = 60;

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Browser {
        @Min(1)
        private int stepTimeoutSeconds = 60;
        private boolean headless = true;
        private boolean sharedProfileEnabled = true;
        private String sharedProfileName = "shared";

        public int getStepTimeoutSeconds() {
            return stepTimeoutSeconds;
        }

        public void setStepTimeoutSeconds(int stepTimeoutSeconds) {
            this.stepTimeoutSeconds = stepTimeoutSeconds;
        }

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public boolean isSharedProfileEnabled() {
            return sharedProfileEnabled;
        }

        public void setSharedProfileEnabled(boolean sharedProfileEnabled) {
            this.sharedProfileEnabled = sharedProfileEnabled;
        }

        public String getSharedProfileName() {
            return sharedProfileName;
        }

        public void setSharedProfileName(String sharedProfileName) {
            this.sharedProfileName = sharedProfileName;
        }
    }

    public static class Retry {
        @Min(0)
        private int maxAttempts = 2;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    public static class Loop {
        @Min(1)
        private int maxRounds = 5;

        public int getMaxRounds() {
            return maxRounds;
        }

        public void setMaxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
        }
    }

    public static class Approval {
        private boolean highRiskEnabled = true;

        public boolean isHighRiskEnabled() {
            return highRiskEnabled;
        }

        public void setHighRiskEnabled(boolean highRiskEnabled) {
            this.highRiskEnabled = highRiskEnabled;
        }
    }

    public static class Api {
        private boolean localOnlyEnabled = true;

        public boolean isLocalOnlyEnabled() {
            return localOnlyEnabled;
        }

        public void setLocalOnlyEnabled(boolean localOnlyEnabled) {
            this.localOnlyEnabled = localOnlyEnabled;
        }
    }

    public static class WebSearch {
        /**
         * Search endpoint templates in priority order.
         * Supported placeholders:
         * - {query}: URL-encoded query
         * - {rawQuery}: raw query text
         * If no placeholder exists, `q=<encoded query>` will be appended.
         */
        private List<String> endpoints = new ArrayList<>();

        public List<String> getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(List<String> endpoints) {
            this.endpoints = endpoints;
        }
    }

}
