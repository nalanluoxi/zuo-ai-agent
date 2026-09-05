package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data
@TableName("t_team")
public class TeamDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String teamName; private Long tenantId; private Long ownerId; private Integer status; private Long parentId;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;

    /** 子部门列表（不映射到数据库，仅用于树形结构传输） */
    @TableField(exist = false)
    private List<TeamDO> children;

    /** 负责人昵称列表（不映射到数据库） */
    @TableField(exist = false)
    private List<String> leaderNames;

    /** 成员昵称预览（负责人优先，最多 4 个，不映射到数据库） */
    @TableField(exist = false)
    private List<String> memberPreviews;

    /** 成员总数（不映射到数据库） */
    @TableField(exist = false)
    private Integer memberCount;
}
