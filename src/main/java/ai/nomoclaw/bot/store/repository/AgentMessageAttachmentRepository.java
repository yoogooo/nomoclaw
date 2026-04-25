package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.AgentMessageAttachmentEntity;
import ai.nomoclaw.bot.store.mapper.AgentMessageAttachmentMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class AgentMessageAttachmentRepository extends CrudRepository<AgentMessageAttachmentMapper, AgentMessageAttachmentEntity> {

    public AgentMessageAttachmentEntity findByUploadUid(String uploadUid) {
        return lambdaQuery()
                .eq(AgentMessageAttachmentEntity::getUploadUid, uploadUid)
                .last("LIMIT 1")
                .one();
    }

    public List<AgentMessageAttachmentEntity> listByMessageUid(String messageUid) {
        return lambdaQuery()
                .eq(AgentMessageAttachmentEntity::getMessageUid, messageUid)
                .eq(AgentMessageAttachmentEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentMessageAttachmentEntity::getId)
                .list();
    }

    public AgentMessageAttachmentEntity findActiveByMessageUidAndFilePath(String messageUid, String filePath) {
        if (messageUid == null || messageUid.isBlank() || filePath == null || filePath.isBlank()) {
            return null;
        }
        return lambdaQuery()
                .eq(AgentMessageAttachmentEntity::getMessageUid, messageUid)
                .eq(AgentMessageAttachmentEntity::getFilePath, filePath)
                .eq(AgentMessageAttachmentEntity::getStatus, "ACTIVE")
                .last("LIMIT 1")
                .one();
    }

    public List<AgentMessageAttachmentEntity> listByMessageUids(Collection<String> messageUids) {
        if (messageUids == null || messageUids.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(AgentMessageAttachmentEntity::getMessageUid, messageUids)
                .eq(AgentMessageAttachmentEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentMessageAttachmentEntity::getId)
                .list();
    }

    public List<AgentMessageAttachmentEntity> listActiveByConversationAndMimeGroup(String conversationUid, String mimeGroup) {
        if (conversationUid == null || conversationUid.isBlank() || mimeGroup == null || mimeGroup.isBlank()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(AgentMessageAttachmentEntity::getConversationUid, conversationUid)
                .eq(AgentMessageAttachmentEntity::getMimeGroup, mimeGroup)
                .eq(AgentMessageAttachmentEntity::getStatus, "ACTIVE")
                .orderByAsc(AgentMessageAttachmentEntity::getId)
                .list();
    }

    public List<AgentMessageAttachmentEntity> listPendingByConversation(String conversationUid) {
        return lambdaQuery()
                .eq(AgentMessageAttachmentEntity::getConversationUid, conversationUid)
                .eq(AgentMessageAttachmentEntity::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.isNull(AgentMessageAttachmentEntity::getMessageUid)
                        .or().eq(AgentMessageAttachmentEntity::getMessageUid, ""))
                .orderByAsc(AgentMessageAttachmentEntity::getId)
                .list();
    }

    public void deleteByConversationUid(String conversationUid) {
        if (conversationUid == null || conversationUid.isBlank()) {
            return;
        }
        lambdaUpdate()
                .eq(AgentMessageAttachmentEntity::getConversationUid, conversationUid)
                .remove();
    }
}
