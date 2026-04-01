package ai.nomoclaw.bot.channel.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_channel_session")
public class AgentChannelSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionUid;
    private String channel;
    private String tenantId;
    private String sessionKey;
    private String conversationUid;
    private String replyTarget;
    private String routeMetadata;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionUid() { return sessionUid; }
    public void setSessionUid(String sessionUid) { this.sessionUid = sessionUid; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
    public String getConversationUid() { return conversationUid; }
    public void setConversationUid(String conversationUid) { this.conversationUid = conversationUid; }
    public String getReplyTarget() { return replyTarget; }
    public void setReplyTarget(String replyTarget) { this.replyTarget = replyTarget; }
    public String getRouteMetadata() { return routeMetadata; }
    public void setRouteMetadata(String routeMetadata) { this.routeMetadata = routeMetadata; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
