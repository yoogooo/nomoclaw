package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.domain.AgentConversation;
import ai.nomoclaw.bot.application.dto.ConversationMessageDto;
import ai.nomoclaw.bot.application.dto.ConversationMessageRunDto;
import ai.nomoclaw.bot.application.dto.ConversationSummaryDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationAppService {

    private final AgentApplicationService facade;

    public ConversationAppService(AgentApplicationService facade) {
        this.facade = facade;
    }

    public String createConversation(String agentGroupUid, String agentUid) {
        return facade.createConversation(agentGroupUid, agentUid);
    }

    public List<ConversationSummaryDto> listConversations() {
        return facade.listConversations();
    }

    public List<ConversationMessageDto> listMessages(String conversationUid) {
        return facade.listMessages(conversationUid);
    }

    public List<ConversationMessageRunDto> listMessageRuns(String conversationUid) {
        return facade.listMessageRuns(conversationUid);
    }

    public void deleteConversation(String conversationUid) {
        facade.deleteConversation(conversationUid);
    }

    public void updateConversationTitle(String conversationUid, String title) {
        facade.updateConversationTitle(conversationUid, title);
    }

    public void updateConversationPinned(String conversationUid, boolean pinned) {
        facade.updateConversationPinned(conversationUid, pinned);
    }

    public AgentConversation getConversation(String conversationUid) {
        return facade.getConversation(conversationUid);
    }
}
