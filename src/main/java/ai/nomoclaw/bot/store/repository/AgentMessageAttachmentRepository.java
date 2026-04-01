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

    public List<AgentMessageAttachmentEntity> listPendingByConversation(String conversationUid) {
        return lambdaQuery()
                .eq(AgentMessageAttachmentEntity::getConversationUid, conversationUid)
                .eq(AgentMessageAttachmentEntity::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.isNull(AgentMessageAttachmentEntity::getMessageUid)
                        .or().eq(AgentMessageAttachmentEntity::getMessageUid, ""))
                .orderByAsc(AgentMessageAttachmentEntity::getId)
                .list();
    }
}
