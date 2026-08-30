package com.example.logmonitor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_app_log")
public class AppLogDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String serviceName; private String hostName; private String traceId;
    private String logLevel; private String loggerName; private String threadName;
    private String message; private String stackTrace;
    private LocalDateTime logTs; private LocalDateTime createTime;
}
