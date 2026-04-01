package ai.nomoclaw.bot.api;

import java.util.List;

public record BatchDeleteCronJobsResponse(
        int requestedCount,
        List<String> deletedJobUids,
        List<FailedItem> failedItems
) {
    public record FailedItem(
            String jobUid,
            String reason
    ) {
    }
}
