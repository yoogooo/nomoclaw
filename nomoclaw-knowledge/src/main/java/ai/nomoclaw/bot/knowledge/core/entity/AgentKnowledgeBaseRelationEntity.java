package ai.nomoclaw.bot.knowledge.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** Stores an Agent's default knowledge-base binding. */
@Getter
@Setter
@TableName("agent_knowledge_base_relation")
public class AgentKnowledgeBaseRelationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String agentUid;
    private String knowledgeBaseUid;
    private Boolean enabled;
    private Date createdTime;
    private Date updatedTime;
}
