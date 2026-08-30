package com.example.authservice.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("t_approval")
public class ApprovalDO {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String applyType; private Long applicantId; private String targetType;
    private Long targetId; private String targetName; private String approverType;
    private Long approverId; private String status; private String rejectReason;
    private LocalDateTime applyTime; private LocalDateTime approveTime;
}
