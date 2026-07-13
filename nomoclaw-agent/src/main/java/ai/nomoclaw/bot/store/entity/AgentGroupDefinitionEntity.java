package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_group_definition")
public class AgentGroupDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentGroupUid;
    private String groupName;
    private String displayName;
    private String avatar;
    private String description;
    private String sceneTags;
    private String collaborationMode;
    private Integer minAgentCount;
    private Integer maxAgentCount;
    private String ownerAgentUid;
    private Integer sortIndex;
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

    public String getAgentGroupUid() {
        return agentGroupUid;
    }

    public void setAgentGroupUid(String agentGroupUid) {
        this.agentGroupUid = agentGroupUid;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
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

    public String getSceneTags() {
        return sceneTags;
    }

    public void setSceneTags(String sceneTags) {
        this.sceneTags = sceneTags;
    }

    public String getCollaborationMode() {
        return collaborationMode;
    }

    public void setCollaborationMode(String collaborationMode) {
        this.collaborationMode = collaborationMode;
    }

    public Integer getMinAgentCount() {
        return minAgentCount;
    }

    public void setMinAgentCount(Integer minAgentCount) {
        this.minAgentCount = minAgentCount;
    }

    public Integer getMaxAgentCount() {
        return maxAgentCount;
    }

    public void setMaxAgentCount(Integer maxAgentCount) {
        this.maxAgentCount = maxAgentCount;
    }

    public String getOwnerAgentUid() {
        return ownerAgentUid;
    }

    public void setOwnerAgentUid(String ownerAgentUid) {
        this.ownerAgentUid = ownerAgentUid;
    }

    public Integer getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(Integer sortIndex) {
        this.sortIndex = sortIndex;
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
