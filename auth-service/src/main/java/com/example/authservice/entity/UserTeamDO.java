package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_user_team")
public class UserTeamDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long userId; private Long teamId; private String roleInTeam;
}
