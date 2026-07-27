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
 * 知识库 Embedding 缓存表
 */
@Getter
@Setter
@TableName("knowledge_embedding_cache")
@Schema(name = "KnowledgeEmbeddingCacheEntity对象", description = "知识库 Embedding 缓存表")
public class KnowledgeEmbeddingCacheEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "缓存键")
    private String cacheKey;

    @Schema(description = "Embedding模型指纹")
    private String modelFingerprint;

    @Schema(description = "内容摘要")
    private String contentHash;

    @Schema(description = "向量维度")
    private Integer dimension;

    @Schema(description = "向量二进制内容")
    private byte[] vectorBlob;

    @Schema(description = "向量字节数")
    private Integer vectorBytes;

    @Schema(description = "命中次数")
    private Long hitCount;

    @Schema(description = "创建时间")
    private Date createdTime;

    @Schema(description = "最后访问时间")
    private Date lastAccessTime;
}
