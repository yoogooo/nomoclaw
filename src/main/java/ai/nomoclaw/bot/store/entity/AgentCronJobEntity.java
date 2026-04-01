package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_cron_job")
public class AgentCronJobEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String jobUid;
    private String agentUid;
    private String conversationUid;
    private String messageUid;
    private String title;
    private String expression;
    private String timezone;
    private String taskContent;
    private String status;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private String lastResult;
    private String extConfig;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
