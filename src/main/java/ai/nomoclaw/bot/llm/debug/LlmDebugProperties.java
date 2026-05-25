package ai.nomoclaw.bot.llm.debug;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 调试日志配置。
 */
@ConfigurationProperties(prefix = "llm.debug")
public class LlmDebugProperties {

    private boolean enabled = false;
    private String filePath = "logs/llm-debug.jsonl";
    private int maxCharsPerField = 20000;
    private boolean redactEnabled = true;
    private boolean includeResponseText = true;
    private long maxFileSizeBytes = 52_428_800L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getMaxCharsPerField() {
        return maxCharsPerField;
    }

    public void setMaxCharsPerField(int maxCharsPerField) {
        this.maxCharsPerField = maxCharsPerField;
    }

    public boolean isRedactEnabled() {
        return redactEnabled;
    }

    public void setRedactEnabled(boolean redactEnabled) {
        this.redactEnabled = redactEnabled;
    }

    public boolean isIncludeResponseText() {
        return includeResponseText;
    }

    public void setIncludeResponseText(boolean includeResponseText) {
        this.includeResponseText = includeResponseText;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}

