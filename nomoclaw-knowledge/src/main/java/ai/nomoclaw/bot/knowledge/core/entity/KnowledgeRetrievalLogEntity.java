package ai.nomoclaw.bot.knowledge.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库检索审计日志表
 */
@Getter
@Setter
@TableName("knowledge_retrieval_log")
@Schema(name = "KnowledgeRetrievalLogEntity对象", description = "知识库检索审计日志表")
public class KnowledgeRetrievalLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "检索业务ID")
    private String retrievalUid;

    @Schema(description = "会话业务ID")
    private String conversationUid;

    @Schema(description = "用户消息业务ID")
    private String messageUid;

    @Schema(description = "检索查询文本")
    private String queryText;

    @Schema(description = "参与检索的知识库ID列表JSON")
    private String knowledgeBaseUids;

    @Schema(description = "Embedding模型指纹")
    private String embeddingModelFingerprint;

    @Schema(description = "候选命中数")
    private Integer candidateCount;

    @Schema(description = "最终选中数")
    private Integer selectedCount;

    @Schema(description = "检索耗时毫秒")
    private Long latencyMs;

    @Schema(description = "检索状态")
    private String status;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private Date createdTime;
}
