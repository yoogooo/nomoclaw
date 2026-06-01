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
        @Min(1)
        private int clickTimeoutSeconds = 15;
        private String mode = "auto";
        private boolean headless = true;
        private boolean forceClickFallbackEnabled = false;
        private boolean sharedProfileEnabled = true;
        private String sharedProfileName = "shared";
        private final LocalBridge localBridge = new LocalBridge();

        public int getStepTimeoutSeconds() {
            return stepTimeoutSeconds;
        }

        public void setStepTimeoutSeconds(int stepTimeoutSeconds) {
            this.stepTimeoutSeconds = stepTimeoutSeconds;
        }

        public int getClickTimeoutSeconds() {
            return clickTimeoutSeconds;
        }

        public void setClickTimeoutSeconds(int clickTimeoutSeconds) {
            this.clickTimeoutSeconds = clickTimeoutSeconds;
        }

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public boolean isForceClickFallbackEnabled() {
            return forceClickFallbackEnabled;
        }

        public void setForceClickFallbackEnabled(boolean forceClickFallbackEnabled) {
            this.forceClickFallbackEnabled = forceClickFallbackEnabled;
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

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public LocalBridge getLocalBridge() {
            return localBridge;
        }
    }

    public static class LocalBridge {
        private String cdpEndpoint = "http://localhost:9222";
        private List<String> localBridgeDomains = new ArrayList<>(List.of("xiaohongshu.com", "*.xiaohongshu.com"));
        private int connectTimeoutMs = 10_000;
        private boolean fallbackToManaged = true;
        private boolean consentRequired = true;

        public String getCdpEndpoint() {
            return cdpEndpoint;
        }

        public void setCdpEndpoint(String cdpEndpoint) {
            this.cdpEndpoint = cdpEndpoint;
        }

        public List<String> getLocalBridgeDomains() {
            return localBridgeDomains;
        }

        public void setLocalBridgeDomains(List<String> localBridgeDomains) {
            this.localBridgeDomains = localBridgeDomains == null ? new ArrayList<>() : new ArrayList<>(localBridgeDomains);
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public boolean isFallbackToManaged() {
            return fallbackToManaged;
        }

        public void setFallbackToManaged(boolean fallbackToManaged) {
            this.fallbackToManaged = fallbackToManaged;
        }

        public boolean isConsentRequired() {
            return consentRequired;
        }

        public void setConsentRequired(boolean consentRequired) {
            this.consentRequired = consentRequired;
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
