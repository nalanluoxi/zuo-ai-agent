package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 实验计划自定义提问
 */
@TableName("t_rag_plan_custom_question")
public class RagPlanCustomQuestionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;
    private String questionText;
    private String expectedAnswer;
    private String expectedDocIds;
    private Long createUserId;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getExpectedAnswer() { return expectedAnswer; }
    public void setExpectedAnswer(String expectedAnswer) { this.expectedAnswer = expectedAnswer; }
    public String getExpectedDocIds() { return expectedDocIds; }
    public void setExpectedDocIds(String expectedDocIds) { this.expectedDocIds = expectedDocIds; }
    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
