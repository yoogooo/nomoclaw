package ai.nomoclaw.bot.system.model;

public record SystemConfigDto(
        String nomoclawRootDir,
        String agentsRootDir,
        String skillsRootDir
) {
}
