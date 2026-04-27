package ai.nomoclaw.bot.mcp;

import ai.nomoclaw.bot.store.entity.McpServerDefinitionEntity;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class McpClientFactory {

    public McpClient create(McpServerDefinitionEntity server, McpServerConfig config) {
        McpTransport transport = createTransport(server, config);
        return DefaultMcpClient.builder()
                .key(server.getServerName())
                .transport(transport)
                .cacheToolList(false)
                .build();
    }

    private McpTransport createTransport(McpServerDefinitionEntity server, McpServerConfig config) {
        String transport = server.getTransport() == null ? "" : server.getTransport().trim().toUpperCase(Locale.ROOT);
        int timeoutSeconds = server.getTimeoutSeconds() == null || server.getTimeoutSeconds() <= 0
                ? 30
                : server.getTimeoutSeconds();
        if ("STDIO".equals(transport)) {
            List<String> command = new ArrayList<>();
            String executable = config.command() == null ? "" : config.command().trim();
            if (executable.isBlank()) {
                throw new IllegalArgumentException("stdio command is required");
            }
            command.add(executable);
            if (config.args() != null) {
                command.addAll(config.args().stream().filter(arg -> arg != null && !arg.isBlank()).toList());
            }
            if (config.cwd() != null && !config.cwd().isBlank()) {
                command = withWorkingDirectory(command, config.cwd().trim());
            }
            return StdioMcpTransport.builder()
                    .command(command)
                    .environment(config.env() == null ? Map.of() : config.env())
                    .logEvents(false)
                    .build();
        }
        String endpoint = config.endpoint() == null ? "" : config.endpoint().trim();
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException("HTTP endpoint is required");
        }
        return StreamableHttpMcpTransport.builder()
                .url(endpoint)
                .customHeaders(config.headers() == null ? Map.of() : config.headers())
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private List<String> withWorkingDirectory(List<String> command, String cwd) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return List.of("cmd", "/c", "cd /d " + windowsQuote(cwd) + " && " + joinWindowsCommand(command));
        }
        return List.of("/bin/sh", "-lc", "cd " + shellQuote(cwd) + " && exec " + joinShellCommand(command));
    }

    private String joinShellCommand(List<String> command) {
        return command.stream().map(this::shellQuote).collect(java.util.stream.Collectors.joining(" "));
    }

    private String joinWindowsCommand(List<String> command) {
        return command.stream().map(this::windowsQuote).collect(java.util.stream.Collectors.joining(" "));
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String windowsQuote(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
