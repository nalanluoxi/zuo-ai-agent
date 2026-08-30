package com.example.logmonitor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_heartbeat")
public class HeartbeatDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String serviceName; private String host; private String status;
    private Double cpuUsage; private Double memoryUsage;
    private Integer activeThreads; private Long gcCount;
    private LocalDateTime heartbeatTime;
    private LocalDateTime createTime;
}
