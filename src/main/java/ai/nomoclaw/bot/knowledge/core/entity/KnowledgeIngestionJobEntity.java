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
 * 知识库导入任务表
 */
@Getter
@Setter
@TableName("knowledge_ingestion_job")
@Schema(name = "KnowledgeIngestionJobEntity对象", description = "知识库导入任务表")
public class KnowledgeIngestionJobEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "任务业务ID")
    private String jobUid;

    @Schema(description = "知识库ID")
    private String knowledgeBaseUid;

    @Schema(description = "文档ID")
    private String documentUid;

    @Schema(description = "文档版本ID")
    private String documentVersionUid;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "处理进度百分比")
    private Integer progressPercent;

    @Schema(description = "总分块数")
    private Integer totalChunks;

    @Schema(description = "已处理分块数")
    private Integer processedChunks;

    @Schema(description = "执行尝试次数")
    private Integer attemptCount;

    @Schema(description = "失败错误码")
    private String failureCode;

    @Schema(description = "失败原因")
    private String failureMessage;

    @Schema(description = "任务租约到期时间")
    private Date leaseUntil;

    @Schema(description = "开始时间")
    private Date startedTime;

    @Schema(description = "完成时间")
    private Date finishedTime;

    @Schema(description = "创建时间")
    private Date createdTime;

    @Schema(description = "更新时间")
    private Date updatedTime;
}
