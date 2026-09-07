package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 灰度发布计划实体
 */
@TableName("t_gray_release_plan")
public class GrayReleasePlanDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String planName;

    /** 发布对象类型: config / prompt / model */
    private String componentType;

    /** 发布对象 ID */
    private Long componentId;

    /** 当前版本（回滚目标） */
    private Long fromVersionId;

    /** 目标版本 */
    private Long toVersionId;

    /** 灰度比例 (0-1) */
    private Double grayRatio;

    /** 灰度模式: PERCENT / LIST / BOTH */
    private String grayMode;

    /** 灰度用户 ID 列表（逗号分隔） */
    private String grayUserIds;

    /** 灰度持续时长（小时） */
    private Integer grayDurationHours;

    /** 状态: DRAFT / APPROVED / GRAYING / ACTIVE / ROLLED_BACK / REJECTED */
    private String status;

    /** 审批人 ID */
    private Long approvedBy;

    /** 审批时间 */
    private Date approvedTime;

    /** 灰度指标快照（JSON） */
    private String grayMetricsSnapshot;

    /** 变更说明 */
    private String changeLog;

    /** 创建人 ID */
    private Long createUserId;

    private Date createTime;
    private Date updateTime;
    private Date finishTime;

    private Long tenantId;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }

    public Long getFromVersionId() { return fromVersionId; }
    public void setFromVersionId(Long fromVersionId) { this.fromVersionId = fromVersionId; }

    public Long getToVersionId() { return toVersionId; }
    public void setToVersionId(Long toVersionId) { this.toVersionId = toVersionId; }

    public Double getGrayRatio() { return grayRatio; }
    public void setGrayRatio(Double grayRatio) { this.grayRatio = grayRatio; }

    public String getGrayMode() { return grayMode; }
    public void setGrayMode(String grayMode) { this.grayMode = grayMode; }

    public String getGrayUserIds() { return grayUserIds; }
    public void setGrayUserIds(String grayUserIds) { this.grayUserIds = grayUserIds; }

    public Integer getGrayDurationHours() { return grayDurationHours; }
    public void setGrayDurationHours(Integer grayDurationHours) { this.grayDurationHours = grayDurationHours; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }

    public Date getApprovedTime() { return approvedTime; }
    public void setApprovedTime(Date approvedTime) { this.approvedTime = approvedTime; }

    public String getGrayMetricsSnapshot() { return grayMetricsSnapshot; }
    public void setGrayMetricsSnapshot(String grayMetricsSnapshot) { this.grayMetricsSnapshot = grayMetricsSnapshot; }

    public String getChangeLog() { return changeLog; }
    public void setChangeLog(String changeLog) { this.changeLog = changeLog; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Date getFinishTime() { return finishTime; }
    public void setFinishTime(Date finishTime) { this.finishTime = finishTime; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
