package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_role_permission")
public class RolePermissionDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long roleId; private Long permissionId;
}
