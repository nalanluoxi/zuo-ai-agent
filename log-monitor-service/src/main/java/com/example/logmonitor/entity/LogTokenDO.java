package com.example.logmonitor.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_log_token")
public class LogTokenDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String token; private String serviceName; private String logLevel;
    private LocalDateTime timeBucket; private Long docCount;
    private LocalDateTime createTime;
}
