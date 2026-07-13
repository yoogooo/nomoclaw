package ai.nomoclaw.bot.llm.codex;

import ai.nomoclaw.bot.util.JsonUtil;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;

public class CodexAuthFileTokenProvider implements CodexTokenProvider {

    private final Path authFile;
    private final Path installationIdFile;

    public CodexAuthFileTokenProvider(Path codexHome) {
        this.authFile = codexHome.resolve("auth.json");
        this.installationIdFile = codexHome.resolve("installation_id");
    }

    @Override
    public String accessToken() {
        return trim(authRoot().path("tokens").path("access_token").asString(""));
    }

    @Override
    public String accountId() {
        return trim(authRoot().path("tokens").path("account_id").asString(""));
    }

    @Override
    public String installationId() {
        try {
            return trim(Files.readString(installationIdFile));
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public AuthStatus authStatus() {
        try {
            if (!Files.isRegularFile(authFile)) {
                return new AuthStatus(false, "missing", "Codex 未登录，请先运行 codex login");
            }
            JsonNode root = authRoot();
            String accessToken = trim(root.path("tokens").path("access_token").asString(""));
            if (accessToken.isBlank()) {
                return new AuthStatus(false, "missing", "Codex 登录 token 缺失，请重新运行 codex login");
            }
            return new AuthStatus(true, "configured", "Codex 登录态可用");
        } catch (Exception ex) {
            return new AuthStatus(false, "error", "Codex 登录态读取失败，请重新运行 codex login");
        }
    }

    private JsonNode authRoot() {
        try {
            if (!Files.isRegularFile(authFile)) {
                throw new IllegalStateException("Codex auth file not found: " + authFile);
            }
            return JsonUtil.fromJson(Files.readString(authFile), JsonNode.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Codex auth file. Please run `codex login`.", ex);
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
