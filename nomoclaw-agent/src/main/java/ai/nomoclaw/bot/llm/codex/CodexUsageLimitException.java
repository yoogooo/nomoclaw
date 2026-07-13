package ai.nomoclaw.bot.llm.codex;

/**
 * Codex API 使用额度已耗尽时抛出的异常。
 */
public final class CodexUsageLimitException extends IllegalStateException {

    private final int statusCode;
    private final String errorType;
    private final String planType;
    private final String apiMessage;
    private final Long resetAtEpochSecond;
    private final Long resetsInSeconds;

    public CodexUsageLimitException(int statusCode,
                                    String errorType,
                                    String planType,
                                    String apiMessage,
                                    Long resetAtEpochSecond,
                                    Long resetsInSeconds) {
        super(apiMessage == null || apiMessage.isBlank()
                ? "Codex usage limit has been reached"
                : apiMessage.trim());
        this.statusCode = statusCode;
        this.errorType = errorType == null ? "" : errorType.trim();
        this.planType = planType == null ? "" : planType.trim();
        this.apiMessage = apiMessage == null ? "" : apiMessage.trim();
        this.resetAtEpochSecond = resetAtEpochSecond;
        this.resetsInSeconds = resetsInSeconds;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorType() {
        return errorType;
    }

    public String planType() {
        return planType;
    }

    public String apiMessage() {
        return apiMessage;
    }

    public Long resetAtEpochSecond() {
        return resetAtEpochSecond;
    }

    public Long resetsInSeconds() {
        return resetsInSeconds;
    }
}
