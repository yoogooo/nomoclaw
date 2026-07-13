package ai.nomoclaw.bot.knowledge.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** Stores conversation-level include and exclude knowledge-base overrides. */
@Getter
@Setter
@TableName("agent_conversation_knowledge_base_relation")
public class ConversationKnowledgeBaseRelationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationUid;
    private String knowledgeBaseUid;
    private String mode;
    private Date createdTime;
    private Date updatedTime;
}
