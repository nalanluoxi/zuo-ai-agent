package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG 测试问题（对应 t_rag_test_question）
 */
@TableName("t_rag_test_question")
public class RagTestQuestionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问题文本 */
    private String questionText;

    /** 期望意图 */
    private String expectedIntent;

    /** 期望改写结果 */
    private String expectedRewritten;

    /** 期望 HyDE 文档 */
    private String expectedHydeDoc;

    /** 期望文档 ID 列表 */
    private String expectedDocIds;

    /** 标准答案 */
    private String standardAnswer;

    /** 分类 */
    private String category;

    /** 难度 */
    private String difficulty;

    /** 租户 ID */
    private Long tenantId;

    /** 创建时间 */
    private Date createTime;

    /** 创建用户 ID */
    private Long createUserId;

    public RagTestQuestionDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getExpectedIntent() { return expectedIntent; }
    public void setExpectedIntent(String expectedIntent) { this.expectedIntent = expectedIntent; }

    public String getExpectedRewritten() { return expectedRewritten; }
    public void setExpectedRewritten(String expectedRewritten) { this.expectedRewritten = expectedRewritten; }

    public String getExpectedHydeDoc() { return expectedHydeDoc; }
    public void setExpectedHydeDoc(String expectedHydeDoc) { this.expectedHydeDoc = expectedHydeDoc; }

    public String getExpectedDocIds() { return expectedDocIds; }
    public void setExpectedDocIds(String expectedDocIds) { this.expectedDocIds = expectedDocIds; }

    public String getStandardAnswer() { return standardAnswer; }
    public void setStandardAnswer(String standardAnswer) { this.standardAnswer = standardAnswer; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }
}
