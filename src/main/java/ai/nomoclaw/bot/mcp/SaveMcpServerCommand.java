package ai.nomoclaw.bot.mcp;

import java.util.List;
import java.util.Map;

public record SaveMcpServerCommand(
        String serverName,
        String displayName,
        String transport,
        Integer timeoutSeconds,
        Boolean autoStart,
        String endpoint,
        Map<String, String> headers,
        String command,
        List<String> args,
        Map<String, String> env,
        String cwd
) {
}
