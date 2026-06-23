package ai.nomoclaw.bot.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.nomoclaw.bot.store.entity.AgentConversationEntity;
import ai.nomoclaw.bot.store.query.ConversationSearchRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentConversationMapper extends BaseMapper<AgentConversationEntity> {

    List<AgentConversationEntity> listConversationPage(@Param("agentUid") String agentUid,
                                                       @Param("asOf") LocalDateTime asOf,
                                                       @Param("beforePinned") Integer beforePinned,
                                                       @Param("beforeLastUserMessageTime") LocalDateTime beforeLastUserMessageTime,
                                                       @Param("beforeId") Long beforeId,
                                                       @Param("limit") int limit);

    List<ConversationSearchRow> searchConversationPage(@Param("agentUid") String agentUid,
                                                       @Param("keywordPattern") String keywordPattern,
                                                       @Param("beforeResultTime") LocalDateTime beforeResultTime,
                                                       @Param("beforeConversationId") Long beforeConversationId,
                                                       @Param("limit") int limit);
}
