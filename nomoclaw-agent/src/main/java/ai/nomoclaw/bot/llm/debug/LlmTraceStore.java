package ai.nomoclaw.bot.llm.debug;

import java.util.List;

/**
 * Provider-neutral persistence for LLM request traces.
 */
public interface LlmTraceStore {

    void save(LlmTraceRecord trace);

    LlmTraceRecord find(String conversationUid, String traceUid);

    List<LlmTraceRecord> listByMessageUid(String conversationUid, String messageUid);
}
