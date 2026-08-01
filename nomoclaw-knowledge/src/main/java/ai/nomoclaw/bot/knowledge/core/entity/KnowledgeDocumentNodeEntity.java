package ai.nomoclaw.bot.knowledge.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Persisted document structure node used as authoritative chunk metadata.
 */
@Getter
@Setter
@TableName("knowledge_document_node")
@Schema(name = "KnowledgeDocumentNodeEntity", description = "知识库文档结构节点")
public class KnowledgeDocumentNodeEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String nodeUid;
    private String knowledgeBaseUid;
    private String documentUid;
    private String documentVersionUid;
    private String parentNodeUid;
    private String nodeType;
    private Integer level;
    private String code;
    private String title;
    private String sectionPath;
    private Integer pageFrom;
    private Integer pageTo;
    private Integer charStart;
    private Integer charEnd;
    private String detectionSource;
    private BigDecimal confidence;
    private Boolean indexable;
    private String metadataJson;
    private Date createdTime;
}
