package ai.nomoclaw.bot.tool;

import java.util.Locale;

public final class PlatformSupport {

    private PlatformSupport() {
    }

    public static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    public static boolean isWindows() {
        return osName().contains("win");
    }

    public static boolean isMac() {
        return osName().contains("mac");
    }

    public static boolean isLinux() {
        String osName = osName();
        return osName.contains("linux") || osName.contains("nix") || osName.contains("nux");
    }
}
