package ai.nomoclaw.bot.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record McpServerConfig(
        String endpoint,
        Map<String, String> headers,
        String command,
        List<String> args,
        Map<String, String> env,
        String cwd
) {
    public static McpServerConfig empty() {
        return new McpServerConfig("", new LinkedHashMap<>(), "", new ArrayList<>(), new LinkedHashMap<>(), "");
    }
}
