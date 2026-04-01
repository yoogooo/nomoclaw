package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_cron_subscription")
public class AgentCronSubscriptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String subscriptionUid;
    private String jobUid;
    private String channel;
    private String target;
    private Integer enabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
