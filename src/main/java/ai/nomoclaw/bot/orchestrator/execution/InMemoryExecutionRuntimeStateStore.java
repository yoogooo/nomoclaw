package ai.nomoclaw.bot.orchestrator.execution;

import dev.langchain4j.data.message.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 运行态存储的内存实现。
 *
 * <p>按 messageUid 聚合管理执行中临时状态，避免编排器直接操作多份并发 Map。
 */
@Component
public class InMemoryExecutionRuntimeStateStore implements ExecutionRuntimeStateStore {

    private final ConcurrentMap<String, Boolean> runningMessages = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MessageExecutionRuntimeState> executionStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StringBuilder> streamingAnswerBuffers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> messageApprovalModes = new ConcurrentHashMap<>();

    @Override
    public boolean start(String messageUid) {
        return runningMessages.putIfAbsent(messageUid, true) == null;
    }

    @Override
    public void finish(String messageUid) {
        runningMessages.remove(messageUid);
    }

    @Override
    public boolean isRunning(String messageUid) {
        return runningMessages.containsKey(messageUid);
    }

    @Override
    public MessageExecutionRuntimeState getOrCreateState(String messageUid, List<ChatMessage> initialMemory) {
        // 首次创建时注入初始 memory；后续复用已有状态，不重复覆盖。
        return executionStates.computeIfAbsent(messageUid, ignored -> new MessageExecutionRuntimeState(initialMemory));
    }

    @Override
    public int currentRound(String messageUid, int defaultValue) {
        MessageExecutionRuntimeState state = executionStates.get(messageUid);
        return state == null ? defaultValue : state.currentRound();
    }

    @Override
    public void appendDelta(String messageUid, String delta) {
        if (delta == null || delta.isBlank()) {
            return;
        }
        streamingAnswerBuffers.computeIfAbsent(messageUid, ignored -> new StringBuilder()).append(delta);
    }

    @Override
    public String getBufferedAnswer(String messageUid) {
        return streamingAnswerBuffers.getOrDefault(messageUid, new StringBuilder()).toString();
    }

    @Override
    public void clearBufferedAnswer(String messageUid) {
        streamingAnswerBuffers.remove(messageUid);
    }

    @Override
    public void setApprovalMode(String messageUid, String approvalMode) {
        messageApprovalModes.put(messageUid, approvalMode);
    }

    @Override
    public String getApprovalMode(String messageUid, String defaultValue) {
        return messageApprovalModes.getOrDefault(messageUid, defaultValue);
    }

    @Override
    public void clear(String messageUid) {
        // 终态统一清理，保证该 message 的运行态无残留。
        runningMessages.remove(messageUid);
        executionStates.remove(messageUid);
        streamingAnswerBuffers.remove(messageUid);
        messageApprovalModes.remove(messageUid);
    }
}
