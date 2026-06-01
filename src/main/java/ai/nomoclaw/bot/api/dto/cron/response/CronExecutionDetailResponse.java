package ai.nomoclaw.bot.api.dto.cron.response;

import ai.nomoclaw.bot.api.dto.conversation.response.ConversationMessageRunResponse;

import java.util.List;

public record CronExecutionDetailResponse(
        String executionUid,
        String jobUid,
        String jobTitle,
        String agentUid,
        String agentDisplayName,
        String conversationUid,
        String messageUid,
        String status,
        String summary,
        String reportPath,
        String reportContent,
        String executedTime,
        List<ConversationMessageRunResponse> runs
) {
}
