package ai.nomoclaw.bot.channel.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Validated
@ConfigurationProperties(prefix = "agent.channels")
public class AgentChannelsProperties {

    private boolean enabled = false;
    private boolean processingAckEnabled = true;
    private String processingAckText = "正在处理，请稍候...";
    @Min(1)
    private int workers = 1;
    @Min(10)
    private int queueSize = 1000;
    private final Feishu feishu = new Feishu();
    private final DingTalk dingtalk = new DingTalk();
    private final Discord discord = new Discord();
    private final Telegram telegram = new Telegram();
    private final Qq qq = new Qq();
    private final WeCom wecom = new WeCom();
    private final Weixin weixin = new Weixin();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isProcessingAckEnabled() {
        return processingAckEnabled;
    }

    public void setProcessingAckEnabled(boolean processingAckEnabled) {
        this.processingAckEnabled = processingAckEnabled;
    }

    public String getProcessingAckText() {
        return processingAckText;
    }

    public void setProcessingAckText(String processingAckText) {
        if (processingAckText == null || processingAckText.isBlank()) {
            this.processingAckText = "已收到，正在处理，请稍候...";
            return;
        }
        this.processingAckText = processingAckText.trim();
    }

    public int getWorkers() {
        return workers;
    }

    public void setWorkers(int workers) {
        this.workers = workers;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public Feishu getFeishu() {
        return feishu;
    }

    public DingTalk getDingtalk() {
        return dingtalk;
    }

    public Discord getDiscord() {
        return discord;
    }

    public Telegram getTelegram() {
        return telegram;
    }

    public Qq getQq() {
        return qq;
    }

    public WeCom getWecom() {
        return wecom;
    }

    public Weixin getWeixin() {
        return weixin;
    }

    public static class BasePlatform {
        private boolean enabled = false;
        private boolean requireMention = false;
        private String allowList = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRequireMention() {
            return requireMention;
        }

        public void setRequireMention(boolean requireMention) {
            this.requireMention = requireMention;
        }

        public String getAllowList() {
            return allowList;
        }

        public void setAllowList(String allowList) {
            this.allowList = allowList;
        }

        public Set<String> allowSet() {
            if (allowList == null || allowList.isBlank()) {
                return Set.of();
            }
            return Arrays.stream(allowList.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    public static class Feishu extends BasePlatform {
        private String appId = "";
        private String appSecret = "";
        private boolean processingAckReactionEnabled = true;
        private String processingAckReactionType = "OK";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public boolean isProcessingAckReactionEnabled() {
            return processingAckReactionEnabled;
        }

        public void setProcessingAckReactionEnabled(boolean processingAckReactionEnabled) {
            this.processingAckReactionEnabled = processingAckReactionEnabled;
        }

        public String getProcessingAckReactionType() {
            return processingAckReactionType;
        }

        public void setProcessingAckReactionType(String processingAckReactionType) {
            if (processingAckReactionType == null || processingAckReactionType.isBlank()) {
                this.processingAckReactionType = "OK";
                return;
            }
            this.processingAckReactionType = processingAckReactionType.trim();
        }
    }

    public static class DingTalk extends BasePlatform {
        private String clientId = "";
        private String clientSecret = "";
        private String robotCode = "";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRobotCode() {
            return robotCode;
        }

        public void setRobotCode(String robotCode) {
            this.robotCode = robotCode;
        }
    }

    public static class Discord extends BasePlatform {
        private String token = "";
        private String botUserId = "";
        private boolean acceptBotMessages = false;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getBotUserId() {
            return botUserId;
        }

        public void setBotUserId(String botUserId) {
            this.botUserId = botUserId;
        }

        public boolean isAcceptBotMessages() {
            return acceptBotMessages;
        }

        public void setAcceptBotMessages(boolean acceptBotMessages) {
            this.acceptBotMessages = acceptBotMessages;
        }
    }

    public static class Telegram extends BasePlatform {
        private String token = "";
        private String botUsername = "";

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getBotUsername() {
            return botUsername;
        }

        public void setBotUsername(String botUsername) {
            this.botUsername = botUsername;
        }
    }

    public static class Qq extends BasePlatform {
        private String appId = "";
        private String clientSecret = "";
        private String botUserId = "";
        private boolean sandbox = false;
        private boolean markdownEnabled = false;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getBotUserId() {
            return botUserId;
        }

        public void setBotUserId(String botUserId) {
            this.botUserId = botUserId;
        }

        public boolean isSandbox() {
            return sandbox;
        }

        public void setSandbox(boolean sandbox) {
            this.sandbox = sandbox;
        }

        public boolean isMarkdownEnabled() {
            return markdownEnabled;
        }

        public void setMarkdownEnabled(boolean markdownEnabled) {
            this.markdownEnabled = markdownEnabled;
        }
    }

    public static class WeCom extends BasePlatform {
        private String wecomBotId = "";
        private String secret = "";

        public String getWecomBotId() {
            return wecomBotId;
        }

        public void setWecomBotId(String wecomBotId) {
            this.wecomBotId = wecomBotId;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

    }

    public static class Weixin extends BasePlatform {
        private String botToken = "";
        private String botTokenFile = "";
        private String baseUrl = "https://ilinkai.weixin.qq.com";

        public String getBotToken() {
            return botToken;
        }

        public void setBotToken(String botToken) {
            this.botToken = botToken;
        }

        public String getBotTokenFile() {
            return botTokenFile;
        }

        public void setBotTokenFile(String botTokenFile) {
            this.botTokenFile = botTokenFile;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
