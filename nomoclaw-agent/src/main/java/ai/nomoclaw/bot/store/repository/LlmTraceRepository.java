package ai.nomoclaw.bot.store.repository;

import ai.nomoclaw.bot.store.entity.LlmTraceEntity;
import ai.nomoclaw.bot.store.mapper.LlmTraceMapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Query and lifecycle operations for LLM traces.
 */
@Repository
public class LlmTraceRepository extends CrudRepository<LlmTraceMapper, LlmTraceEntity> {

    public List<LlmTraceEntity> listByMessageUid(String messageUid) {
        return lambdaQuery()
                .eq(LlmTraceEntity::getMessageUid, messageUid)
                .orderByAsc(LlmTraceEntity::getRequestStartedTime)
                .orderByAsc(LlmTraceEntity::getId)
                .list();
    }

    public LlmTraceEntity findByTraceUid(String traceUid) {
        return lambdaQuery().eq(LlmTraceEntity::getTraceUid, traceUid).one();
    }

    public void deleteByConversationUid(String conversationUid) {
        lambdaUpdate().eq(LlmTraceEntity::getConversationUid, conversationUid).remove();
    }
}
