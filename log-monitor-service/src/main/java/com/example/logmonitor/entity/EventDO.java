package com.example.logmonitor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_event")
public class EventDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String eventType; private String source; private String message;
    private String serviceName; private String host;
    private LocalDateTime eventTime; private String metadata;
    private LocalDateTime createTime;
}
