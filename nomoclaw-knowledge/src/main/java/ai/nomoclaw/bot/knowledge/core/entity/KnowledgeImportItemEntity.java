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
 * 知识库导入项表
 */
@Getter
@Setter
@TableName("knowledge_import_item")
@Schema(name = "KnowledgeImportItemEntity对象", description = "知识库导入项表")
public class KnowledgeImportItemEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "导入项业务ID")
    private String itemUid;

    @Schema(description = "导入批次业务ID")
    private String batchUid;

    @Schema(description = "文档业务ID")
    private String documentUid;

    @Schema(description = "文档版本业务ID")
    private String documentVersionUid;

    @Schema(description = "原始文件名")
    private String originalFileName;

    @Schema(description = "导入模式")
    private String mode;

    @Schema(description = "接收结果")
    private String outcome;

    @Schema(description = "处理状态")
    private String status;

    @Schema(description = "错误码")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private Date createdTime;

    @Schema(description = "更新时间")
    private Date updatedTime;
}
