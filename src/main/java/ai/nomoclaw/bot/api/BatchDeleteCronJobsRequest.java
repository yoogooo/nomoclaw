package ai.nomoclaw.bot.api;

import java.util.List;

public record BatchDeleteCronJobsRequest(
        List<String> jobUids
) {
}
