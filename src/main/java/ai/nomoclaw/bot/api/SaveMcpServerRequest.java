package ai.nomoclaw.bot.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record SaveMcpServerRequest(
        @NotBlank @Size(max = 100) String serverName,
        @NotBlank String transport,
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
