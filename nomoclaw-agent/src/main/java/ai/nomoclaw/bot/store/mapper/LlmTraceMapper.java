package ai.nomoclaw.bot.store.mapper;

import ai.nomoclaw.bot.store.entity.LlmTraceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Persistence mapper for LLM traces.
 */
@Mapper
public interface LlmTraceMapper extends BaseMapper<LlmTraceEntity> {
}
