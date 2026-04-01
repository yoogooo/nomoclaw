package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("agent_message_attachment")
public class AgentMessageAttachmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadUid;
    private String conversationUid;
    private String messageUid;
    private String originalName;
    private String contentType;
    private String mimeGroup;
    private String filePath;
    private String fileUrl;
    private Long sizeBytes;
    private Integer previewable;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUploadUid() { return uploadUid; }
    public void setUploadUid(String uploadUid) { this.uploadUid = uploadUid; }
    public String getConversationUid() { return conversationUid; }
    public void setConversationUid(String conversationUid) { this.conversationUid = conversationUid; }
    public String getMessageUid() { return messageUid; }
    public void setMessageUid(String messageUid) { this.messageUid = messageUid; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getMimeGroup() { return mimeGroup; }
    public void setMimeGroup(String mimeGroup) { this.mimeGroup = mimeGroup; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getPreviewable() { return previewable; }
    public void setPreviewable(Integer previewable) { this.previewable = previewable; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
