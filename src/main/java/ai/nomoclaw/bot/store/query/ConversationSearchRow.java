package ai.nomoclaw.bot.store.query;

import java.time.LocalDateTime;

public class ConversationSearchRow {

    private Long conversationId;
    private String conversationUid;
    private String agentGroupUid;
    private String agentUid;
    private String title;
    private String previewText;
    private LocalDateTime resultTime;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getConversationUid() {
        return conversationUid;
    }

    public void setConversationUid(String conversationUid) {
        this.conversationUid = conversationUid;
    }

    public String getAgentGroupUid() {
        return agentGroupUid;
    }

    public void setAgentGroupUid(String agentGroupUid) {
        this.agentGroupUid = agentGroupUid;
    }

    public String getAgentUid() {
        return agentUid;
    }

    public void setAgentUid(String agentUid) {
        this.agentUid = agentUid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPreviewText() {
        return previewText;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    public LocalDateTime getResultTime() {
        return resultTime;
    }

    public void setResultTime(LocalDateTime resultTime) {
        this.resultTime = resultTime;
    }
}
