package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_team")
public class TeamDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String teamName; private Long tenantId; private Long ownerId; private Integer status; private Long parentId;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
