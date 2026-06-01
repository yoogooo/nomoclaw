package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_cron_job_execution")
public class AgentCronJobExecutionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String executionUid;
    private String jobUid;
    private String agentUid;
    private String conversationUid;
    private String messageUid;
    private String status;
    private String summary;
    private String reportPath;
    private LocalDateTime approvalWaitStartedTime;
    private Integer approvalTimeoutSeconds;
    private Integer resumeRequested;
    private Integer readFlag;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
