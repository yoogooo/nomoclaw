package ai.nomoclaw.bot.mcp;

import java.util.Map;

public record CreateCodexMcpServerParam(
        String serverName,
        Integer timeoutSeconds,
        Boolean autoStart,
        String command,
        String cwd,
        Map<String, String> env
) {
}
