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
 * 知识库文档表
 */
@Getter
@Setter
@TableName("knowledge_document")
@Schema(name = "KnowledgeDocumentEntity对象", description = "知识库文档表")
public class KnowledgeDocumentEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "文档业务ID")
    private String documentUid;

    @Schema(description = "所属知识库ID")
    private String knowledgeBaseUid;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "来源类型，首版为UPLOAD")
    private String sourceType;

    @Schema(description = "原始文件名")
    private String originalFileName;

    @Schema(description = "文件MIME类型")
    private String contentType;

    @Schema(description = "本地文件路径")
    private String filePath;

    @Schema(description = "文件字节数")
    private Long sizeBytes;

    @Schema(description = "文件SHA-256摘要")
    private String checksumSha256;

    @Schema(description = "当前可检索版本ID")
    private String currentVersionUid;

    @Schema(description = "状态：UPLOADED/PROCESSING/READY/FAILED/DELETING")
    private String status;

    @Schema(description = "失败错误码")
    private String failureCode;

    @Schema(description = "失败原因")
    private String failureMessage;

    @Schema(description = "解析页数")
    private Integer pageCount;

    @Schema(description = "分块数量")
    private Integer chunkCount;

    @Schema(description = "创建时间")
    private Date createdTime;

    @Schema(description = "更新时间")
    private Date updatedTime;
}
