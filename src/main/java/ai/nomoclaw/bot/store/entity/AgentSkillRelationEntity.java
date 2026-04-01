package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_skill_relation")
public class AgentSkillRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String relationUid;
    private String agentUid;
    private String skillKey;
    private String status;
    private Integer sortIndex;
    private String configJson;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
