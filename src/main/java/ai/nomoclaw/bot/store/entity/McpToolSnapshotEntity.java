package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mcp_tool_snapshot")
public class McpToolSnapshotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotUid;
    private String serverUid;
    private String toolKey;
    private String originalToolName;
    private String description;
    private String inputSchemaJson;
    private String status;
    private LocalDateTime lastSyncedTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
