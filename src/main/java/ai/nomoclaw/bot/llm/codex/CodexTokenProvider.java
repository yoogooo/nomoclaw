package ai.nomoclaw.bot.llm.codex;

public interface CodexTokenProvider {

    String accessToken();

    String accountId();

    String installationId();

    AuthStatus authStatus();

    record AuthStatus(boolean configured, String status, String message) {
    }
}
