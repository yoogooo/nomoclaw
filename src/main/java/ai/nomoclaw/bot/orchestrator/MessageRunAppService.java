package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.domain.AgentMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
public class MessageRunAppService {

    private final AgentApplicationService facade;

    public MessageRunAppService(AgentApplicationService facade) {
        this.facade = facade;
    }

    public String submitMessage(String conversationUid, String message) {
        return facade.submitMessage(conversationUid, message);
    }

    public String submitMessage(String conversationUid,
                                String message,
                                List<String> fileUrls,
                                String modelProvider,
                                String modelName) {
        return facade.submitMessage(conversationUid, message, fileUrls, modelProvider, modelName, "default", "web", null);
    }

    public String submitMessage(String conversationUid,
                                String message,
                                List<String> fileUrls,
                                String modelProvider,
                                String modelName,
                                String approvalMode) {
        return facade.submitMessage(conversationUid, message, fileUrls, modelProvider, modelName, approvalMode, "web", null);
    }

    public AgentMessage getMessage(String messageUid) {
        return facade.getMessage(messageUid);
    }

    public int maxLoopRounds() {
        return facade.maxLoopRounds();
    }

    public void cancelLatestMessage(String conversationUid) {
        facade.cancelLatestMessage(conversationUid);
    }

    public SseEmitter subscribe(String conversationUid) {
        return facade.subscribe(conversationUid);
    }
}
