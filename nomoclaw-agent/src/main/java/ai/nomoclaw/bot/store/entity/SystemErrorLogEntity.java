package ai.nomoclaw.bot.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_error_log")
public class SystemErrorLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String logUid;
    private String level;
    private String source;
    private String code;
    private String title;
    private String message;
    private String detail;
    private LocalDateTime occurredTime;
    private LocalDateTime createdTime;
}
