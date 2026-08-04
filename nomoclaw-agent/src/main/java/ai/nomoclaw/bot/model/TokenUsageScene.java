package ai.nomoclaw.bot.model;

/**
 * Identifies the product operation that consumed model tokens.
 */
public enum TokenUsageScene {
    CHAT_REASONING,
    CHAT_SUMMARY,
    TIP_SUMMARY
}
