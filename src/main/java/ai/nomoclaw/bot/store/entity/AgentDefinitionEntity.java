package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_definition")
public class AgentDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentUid;
    private String agentName;
    private String displayName;
    private String avatar;
    private String description;
    private String capabilityTags;
    private String promptProfile;
    @TableField("model_provider_id")
    private String modelProviderId;
    @TableField("model_id")
    private String modelId;
    private Integer sortIndex;
    private Integer isGroupEntry;
    private String status;
    private String extConfig;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentUid() {
        return agentUid;
    }

    public void setAgentUid(String agentUid) {
        this.agentUid = agentUid;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCapabilityTags() {
        return capabilityTags;
    }

    public void setCapabilityTags(String capabilityTags) {
        this.capabilityTags = capabilityTags;
    }

    public String getPromptProfile() {
        return promptProfile;
    }

    public void setPromptProfile(String promptProfile) {
        this.promptProfile = promptProfile;
    }

    public String getModelProviderId() {
        return modelProviderId;
    }

    public void setModelProviderId(String modelProviderId) {
        this.modelProviderId = modelProviderId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public Integer getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(Integer sortIndex) {
        this.sortIndex = sortIndex;
    }

    public Integer getIsGroupEntry() {
        return isGroupEntry;
    }

    public void setIsGroupEntry(Integer isGroupEntry) {
        this.isGroupEntry = isGroupEntry;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExtConfig() {
        return extConfig;
    }

    public void setExtConfig(String extConfig) {
        this.extConfig = extConfig;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }
}
