package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_tip")
public class AgentTipEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tipUid;
    private String agentUid;
    private String title;
    private String summary;
    private String sourceContent;
    private String sourceConversationUid;
    private String sourceMessageUid;
    private LocalDateTime sourceTime;
    private String status;
    private Integer sortIndex;
    private String extConfig;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
