package ai.nomoclaw.bot.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.nomoclaw.bot.store.entity.AgentMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessageEntity> {
}
