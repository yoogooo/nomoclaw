package ai.nomoclaw.bot.knowledge.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * Minimal projection of agent conversations used by knowledge-base bindings.
 */
@Getter
@Setter
@TableName("agent_conversation")
public class AgentConversationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationUid;
    private String agentUid;
}
