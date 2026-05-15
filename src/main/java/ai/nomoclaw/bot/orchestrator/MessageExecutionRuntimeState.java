package ai.nomoclaw.bot.orchestrator;

import dev.langchain4j.data.message.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public final class MessageExecutionRuntimeState {

    private final List<ChatMessage> memory = new ArrayList<>();
    private int currentRound = 1;

    public MessageExecutionRuntimeState(List<ChatMessage> initialMemory) {
        if (initialMemory != null) {
            this.memory.addAll(initialMemory);
        }
    }

    public List<ChatMessage> memory() {
        return memory;
    }

    public int currentRound() {
        return currentRound;
    }

    public void advanceRound() {
        currentRound++;
    }
}
