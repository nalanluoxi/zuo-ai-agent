package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_user")
public class UserDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String username; private String password; private String nickname; private String email;
    private Long tenantId; private Integer status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
