package ai.nomoclaw.bot.knowledge.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/** Stores a retrieved chunk citation for a user message. */
@Getter
@Setter
@TableName("agent_message_knowledge_citation")
public class MessageKnowledgeCitationEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageUid;
    private String assistantMessageUid;
    private String retrievalUid;
    private String chunkUid;
    private Integer rankIndex;
    private Double score;
    private Date createdTime;
}
