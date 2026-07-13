package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tool_definition")
public class ToolDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String toolKey;
    private String displayName;
    private String description;
    private String riskLevel;
    private String status;
    private Integer sortIndex;
    private String configJson;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
