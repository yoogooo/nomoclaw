package ai.nomoclaw.bot.application.dto;

public record SystemConfigDto(
        String nomoclawRootDir,
        String agentsRootDir,
        String skillsRootDir
) {
}
