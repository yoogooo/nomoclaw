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
 * 知识库定义表
 */
@Getter
@Setter
@TableName("knowledge_base")
@Schema(name = "KnowledgeBaseEntity对象", description = "知识库定义表")
public class KnowledgeBaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "知识库业务ID")
    private String knowledgeBaseUid;

    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "知识库描述")
    private String description;

    @Schema(description = "状态：ACTIVE/DISABLED/DELETING/ERROR")
    private String status;

    @Schema(description = "Embedding 服务商ID")
    private String embeddingProviderId;

    @Schema(description = "Embedding 模型ID")
    private String embeddingModelId;

    @Schema(description = "向量维度")
    private Integer embeddingDimension;

    @Schema(description = "Qdrant Collection名称")
    private String vectorCollectionName;

    @Schema(description = "默认分块Token数")
    private Integer chunkSizeTokens;

    @Schema(description = "分块重叠Token数")
    private Integer chunkOverlapTokens;

    @Schema(description = "默认召回数量")
    private Integer retrievalTopK;

    @Schema(description = "相似度阈值")
    private Double similarityThreshold;

    @Schema(description = "就绪文档数量")
    private Integer documentCount;

    @Schema(description = "就绪分块数量")
    private Long chunkCount;

    @Schema(description = "创建时间")
    private Date createdTime;

    @Schema(description = "更新时间")
    private Date updatedTime;
}
