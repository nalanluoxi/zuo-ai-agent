package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG 测试文档（对应 t_rag_test_document）
 */
@TableName("t_rag_test_document")
public class RagTestDocumentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档标题 */
    private String docTitle;

    /** 文档内容 */
    private String docContent;

    /** 文档分类 */
    private String docCategory;

    /** 租户 ID */
    private Long tenantId;

    /** 创建时间 */
    private Date createTime;

    /** 创建用户 ID */
    private Long createUserId;

    public RagTestDocumentDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocTitle() { return docTitle; }
    public void setDocTitle(String docTitle) { this.docTitle = docTitle; }

    public String getDocContent() { return docContent; }
    public void setDocContent(String docContent) { this.docContent = docContent; }

    public String getDocCategory() { return docCategory; }
    public void setDocCategory(String docCategory) { this.docCategory = docCategory; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }
}
