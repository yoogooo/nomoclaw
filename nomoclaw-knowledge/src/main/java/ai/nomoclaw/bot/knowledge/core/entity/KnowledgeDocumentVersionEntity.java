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
 * 知识库文档版本表
 */
@Getter
@Setter
@TableName("knowledge_document_version")
@Schema(name = "KnowledgeDocumentVersionEntity对象", description = "知识库文档版本表")
public class KnowledgeDocumentVersionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "文档版本业务ID")
    private String documentVersionUid;

    @Schema(description = "文档业务ID")
    private String documentUid;

    @Schema(description = "版本号")
    private Integer versionNo;

    @Schema(description = "文件SHA-256摘要")
    private String checksumSha256;

    @Schema(description = "解析器版本")
    private String parserVersion;

    @Schema(description = "分块器版本")
    private String chunkerVersion;

    @Schema(description = "Embedding模型指纹")
    private String embeddingModelFingerprint;

    @Schema(description = "版本状态")
    private String status;

    @Schema(description = "创建时间")
    private Date createdTime;
}
