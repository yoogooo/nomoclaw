package ai.nomoclaw.bot.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.nomoclaw.bot.store.entity.AgentStepEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentStepMapper extends BaseMapper<AgentStepEntity> {
}
