package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("token_usage_record")
public class TokenUsageRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordUid;
    private String scene;
    private String provider;
    private String modelName;
    private String conversationUid;
    private String messageUid;
    private Integer inputTokens;
    private Integer cachedInputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private Boolean usageAvailable;
    private LocalDateTime occurredTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRecordUid() { return recordUid; }
    public void setRecordUid(String recordUid) { this.recordUid = recordUid; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getConversationUid() { return conversationUid; }
    public void setConversationUid(String conversationUid) { this.conversationUid = conversationUid; }
    public String getMessageUid() { return messageUid; }
    public void setMessageUid(String messageUid) { this.messageUid = messageUid; }
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getCachedInputTokens() { return cachedInputTokens; }
    public void setCachedInputTokens(Integer cachedInputTokens) { this.cachedInputTokens = cachedInputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }
    public Boolean getUsageAvailable() { return usageAvailable; }
    public void setUsageAvailable(Boolean usageAvailable) { this.usageAvailable = usageAvailable; }
    public LocalDateTime getOccurredTime() { return occurredTime; }
    public void setOccurredTime(LocalDateTime occurredTime) { this.occurredTime = occurredTime; }
}
