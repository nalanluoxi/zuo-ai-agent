package com.example.logmonitor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_alert_record")
public class AlertRecordDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long ruleId; private String alertLevel; private String alertMessage;
    private Double metricValue; private String status;
    private LocalDateTime fireTime; private LocalDateTime resolveTime;
}
