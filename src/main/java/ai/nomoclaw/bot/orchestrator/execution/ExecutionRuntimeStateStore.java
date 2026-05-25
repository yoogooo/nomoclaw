package ai.nomoclaw.bot.orchestrator.execution;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 消息执行期运行态存储抽象。
 *
 * <p>用于统一托管幂等执行标记、轮次上下文、流式输出缓冲与审批模式。
 */
public interface ExecutionRuntimeStateStore {

    boolean start(String messageUid);

    void finish(String messageUid);

    boolean isRunning(String messageUid);

    MessageExecutionRuntimeState getOrCreateState(String messageUid, List<ChatMessage> initialMemory);

    int currentRound(String messageUid, int defaultValue);

    void appendDelta(String messageUid, String delta);

    String getBufferedAnswer(String messageUid);

    void clearBufferedAnswer(String messageUid);

    void setApprovalMode(String messageUid, String approvalMode);

    String getApprovalMode(String messageUid, String defaultValue);

    void bindConversation(String messageUid, String conversationUid);

    List<String> listActiveMessageUids(String conversationUid);

    void clear(String messageUid);
}
