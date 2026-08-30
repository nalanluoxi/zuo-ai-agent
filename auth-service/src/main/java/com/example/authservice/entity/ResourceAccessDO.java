package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_resource_access")
public class ResourceAccessDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String resourceType; private Long resourceId; private String granteeType;
    private Long granteeId; private String permission; private Long grantedBy;
    private LocalDateTime createTime;
}
