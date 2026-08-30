package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_audit_log")
public class AuditLogDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long userId; private String action; private String targetType;
    private Long targetId; private String detail; private String ipAddress;
    private String userAgent; private LocalDateTime createTime;
}
