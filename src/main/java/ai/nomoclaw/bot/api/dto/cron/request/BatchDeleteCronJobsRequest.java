package ai.nomoclaw.bot.api.dto.cron.request;

import java.util.List;

public record BatchDeleteCronJobsRequest(
        List<String> jobUids
) {
}
