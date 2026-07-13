package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mcp_server_definition")
public class McpServerDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String serverUid;
    private String serverName;
    private String transport;
    private String status;
    private Integer timeoutSeconds;
    private Boolean autoStart;
    private String configJson;
    private LocalDateTime lastConnectedTime;
    private String lastError;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
