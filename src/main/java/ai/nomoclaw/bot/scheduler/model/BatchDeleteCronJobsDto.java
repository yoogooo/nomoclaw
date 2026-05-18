package ai.nomoclaw.bot.scheduler.model;

import java.util.List;

public record BatchDeleteCronJobsDto(
        int requestedCount,
        List<String> deletedJobUids,
        List<FailedItemDto> failedItems
) {
    public record FailedItemDto(String jobUid, String reason) {
    }
}
