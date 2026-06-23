package ai.nomoclaw.bot.conversation.app;

import ai.nomoclaw.bot.orchestrator.AgentApplicationService;

import ai.nomoclaw.bot.conversation.model.ConversationMessageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageAnchorDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessagePageDto;
import ai.nomoclaw.bot.conversation.model.ConversationMessageRunDto;
import ai.nomoclaw.bot.conversation.model.ConversationSearchPageDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryDto;
import ai.nomoclaw.bot.conversation.model.ConversationSummaryPageDto;
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

    public ConversationSummaryPageDto listConversationPage(String agentUid, Integer limit, String beforeSortKey, String asOf) {
        return facade.listConversationPage(agentUid, limit, beforeSortKey, asOf);
    }

    public ConversationSearchPageDto searchConversationPage(String agentUid, String keyword, Integer limit, String beforeSortKey) {
        return facade.searchConversationPage(agentUid, keyword, limit, beforeSortKey);
    }

    public List<ConversationMessageDto> listMessages(String conversationUid) {
        return facade.listMessages(conversationUid);
    }

    public ConversationMessagePageDto listMessagePage(String conversationUid, Integer limit, String beforeMessageUid) {
        return facade.listMessagePage(conversationUid, limit, beforeMessageUid);
    }

    public ConversationMessageAnchorDto loadMessageAnchor(String conversationUid, String keyword, Integer beforeLimit, Integer afterLimit) {
        return facade.loadMessageAnchor(conversationUid, keyword, beforeLimit, afterLimit);
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

    public void markConversationRead(String conversationUid) {
        facade.markConversationRead(conversationUid);
    }
}
