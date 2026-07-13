package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tool_relation")
public class AgentToolRelationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String relationUid;
    private String agentUid;
    private String toolKey;
    private String status;
    private Integer sortIndex;
    private String configJson;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
