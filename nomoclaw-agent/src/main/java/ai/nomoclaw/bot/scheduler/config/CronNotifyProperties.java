package ai.nomoclaw.bot.scheduler.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "agent.cron.notify")
public class CronNotifyProperties {

    @Min(1)
    private int retryMax = 3;

    @Min(1)
    private long retryBackoffMs = 1000L;

    @Min(1)
    private int summaryMaxLines = 12;

    public int getRetryMax() {
        return retryMax;
    }

    public void setRetryMax(int retryMax) {
        this.retryMax = retryMax;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public int getSummaryMaxLines() {
        return summaryMaxLines;
    }

    public void setSummaryMaxLines(int summaryMaxLines) {
        this.summaryMaxLines = summaryMaxLines;
    }
}
