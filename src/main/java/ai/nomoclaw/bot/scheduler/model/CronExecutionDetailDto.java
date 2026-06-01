package ai.nomoclaw.bot.scheduler.model;

import ai.nomoclaw.bot.conversation.model.ConversationMessageRunDto;

import java.util.List;

public record CronExecutionDetailDto(
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
        List<ConversationMessageRunDto> runs
) {
}
