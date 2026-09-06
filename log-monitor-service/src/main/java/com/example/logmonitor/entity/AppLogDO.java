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

    // Phase 4: CAT 风格日志扩展字段
    private String logType;          // normal / transaction / event
    private String spanId;           // 当前 span ID
    private String parentTraceId;    // 父级 trace ID（跨服务调用链）
    private String eventType;        // 事件类型（如 HyDE_GENERATE, INTENT_CLASSIFY 等）
    private Long nodeId;             // 关联的 trace node ID
    private String metadata;         // 扩展元数据（JSON）
}
