package ai.nomoclaw.bot.knowledge.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 知识库导入批次表
 */
@Getter
@Setter
@TableName("knowledge_import_batch")
@Schema(name = "KnowledgeImportBatchEntity对象", description = "知识库导入批次表")
public class KnowledgeImportBatchEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "导入批次业务ID")
    private String batchUid;

    @Schema(description = "知识库业务ID")
    private String knowledgeBaseUid;

    @Schema(description = "批次状态")
    private String status;

    @Schema(description = "解析模式")
    private String parserMode;

    @Schema(description = "分块Token数")
    private Integer chunkSizeTokens;

    @Schema(description = "分块重叠Token数")
    private Integer chunkOverlapTokens;

    @Schema(description = "Embedding 服务商ID")
    private String embeddingProviderId;

    @Schema(description = "Embedding 模型ID")
    private String embeddingModelId;

    @Schema(description = "向量维度")
    private Integer embeddingDimension;

    @Schema(description = "Embedding模型指纹")
    private String embeddingModelFingerprint;

    @Schema(description = "预处理配置")
    private String preprocessingConfig;

    @Schema(description = "构建配置摘要")
    private String configHash;

    @Schema(description = "创建时间")
    private Date createdTime;

    @Schema(description = "更新时间")
    private Date updatedTime;
}
