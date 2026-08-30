package com.example.logmonitor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_trace_span")
public class TraceSpanDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String traceId; private String spanId; private String parentSpanId;
    private String operationName; private String serviceName;
    private LocalDateTime startTime; private Double durationMs;
    private String status; private String tags;
    private LocalDateTime createTime;
}
