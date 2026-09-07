package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 灰度发布计划-模型关联表
 */
@TableName("t_gray_release_plan_model")
public class GrayReleasePlanModelDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long modelConfigId;
    private Short fromIsActive;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getModelConfigId() { return modelConfigId; }
    public void setModelConfigId(Long modelConfigId) { this.modelConfigId = modelConfigId; }
    public Short getFromIsActive() { return fromIsActive; }
    public void setFromIsActive(Short fromIsActive) { this.fromIsActive = fromIsActive; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
