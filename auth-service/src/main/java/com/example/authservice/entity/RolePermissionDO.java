package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("t_role_permission")
public class RolePermissionDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long roleId;
    private Long permissionId;
    /** 访问级别：READ 只读 / WRITE 修改 / ADMIN 超级管理 */
    private String accessLevel;
}
