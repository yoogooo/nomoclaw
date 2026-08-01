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
 * 知识库文档分块表
 */
@Getter
@Setter
@TableName("knowledge_chunk")
@Schema(name = "KnowledgeChunkEntity对象", description = "知识库文档分块表")
public class KnowledgeChunkEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "分块业务ID")
    private String chunkUid;

    @Schema(description = "所属知识库ID")
    private String knowledgeBaseUid;

    @Schema(description = "所属文档ID")
    private String documentUid;

    @Schema(description = "所属文档版本ID")
    private String documentVersionUid;

    @Schema(description = "所属文档结构节点ID")
    private String documentNodeUid;

    @Schema(description = "文档内分块序号")
    private Integer chunkIndex;

    @Schema(description = "分块正文")
    private String content;

    @Schema(description = "估算Token数")
    private Integer tokenCount;

    @Schema(description = "正文SHA-256摘要")
    private String contentHash;

    @Schema(description = "起始页码")
    private Integer pageFrom;

    @Schema(description = "结束页码")
    private Integer pageTo;

    @Schema(description = "章节路径")
    private String sectionPath;

    @Schema(description = "原文起始字符偏移")
    private Integer charStart;

    @Schema(description = "原文结束字符偏移")
    private Integer charEnd;

    @Schema(description = "Qdrant向量点ID")
    private String vectorPointId;

    @Schema(description = "分块索引状态")
    private String status;

    @Schema(description = "创建时间")
    private Date createdTime;
}
