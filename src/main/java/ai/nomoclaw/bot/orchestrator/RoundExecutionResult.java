package ai.nomoclaw.bot.orchestrator;

public record RoundExecutionResult(boolean waitingApproval, boolean canceled) {

    public static RoundExecutionResult success() {
        return new RoundExecutionResult(false, false);
    }

    public static RoundExecutionResult pendingApproval() {
        return new RoundExecutionResult(true, false);
    }

    public static RoundExecutionResult cancelled() {
        return new RoundExecutionResult(false, true);
    }
}
