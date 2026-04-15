package ai.nomoclaw.bot.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandShellResolver {

    private CommandShellResolver() {
    }

    public static CommandShell resolve() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return resolveWindowsShell();
        }
        return resolveUnixShell();
    }

    private static CommandShell resolveWindowsShell() {
        String comSpec = System.getenv("ComSpec");
        if (comSpec != null && !comSpec.isBlank()) {
            Path path = Path.of(comSpec.trim());
            if (Files.isRegularFile(path)) {
                return new CommandShell(displayName(path), path.toString(), List.of("/S", "/C"));
            }
        }
        return new CommandShell("cmd.exe", "cmd.exe", List.of("/S", "/C"));
    }

    private static CommandShell resolveUnixShell() {
        Set<String> candidates = new LinkedHashSet<>();
        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            candidates.add(shell.trim());
        }
        candidates.add("/bin/zsh");
        candidates.add("/usr/bin/zsh");
        candidates.add("/bin/bash");
        candidates.add("/usr/bin/bash");
        candidates.add("/bin/sh");
        candidates.add("/usr/bin/sh");

        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            String displayName = displayName(path);
            if (isSupportedUnixShell(displayName) && Files.isRegularFile(path) && Files.isExecutable(path)) {
                return new CommandShell(displayName, path.toString(), unixShellArgs(displayName));
            }
        }
        return new CommandShell("sh", "sh", List.of("-c"));
    }

    private static boolean isSupportedUnixShell(String displayName) {
        return "zsh".equals(displayName) || "bash".equals(displayName) || "sh".equals(displayName);
    }

    private static List<String> unixShellArgs(String displayName) {
        return "sh".equals(displayName) ? List.of("-c") : List.of("-lc");
    }

    private static String displayName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    public record CommandShell(String displayName, String executable, List<String> argsBeforeCommand) {
        public List<String> command(String command) {
            List<String> result = new ArrayList<>(argsBeforeCommand.size() + 2);
            result.add(executable);
            result.addAll(argsBeforeCommand);
            result.add(command);
            return result;
        }
    }
}
