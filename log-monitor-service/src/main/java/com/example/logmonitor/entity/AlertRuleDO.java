package com.example.logmonitor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_alert_rule")
public class AlertRuleDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String ruleName; private String metric; private String condition;
    private Double threshold; private Integer windowMinutes;
    private String notifyChannels; private Boolean enabled;
}
