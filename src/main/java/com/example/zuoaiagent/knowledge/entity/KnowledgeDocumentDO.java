package com.example.zuoaiagent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 知识库文档实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_knowledge_document")
public class KnowledgeDocumentDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属知识库 ID */
    private Long kbId;

    /** 文档名称 */
    private String docName;

    /** 文件存储地址（MinIO 路径） */
    private String fileUrl;

    /** 文件类型：pdf / markdown / docx 等 */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 来源类型：file */
    private String sourceType;

    /**
     * 文档状态：pending / success / failed
     */
    private String status;

    /** 是否启用：1-启用，0-禁用 */
    private Integer enabled;

    /** 创建人 */
    private String createdBy;

    /** 修改人 */
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 是否删除：0-正常，1-删除 */
    @TableLogic
    private Integer deleted;
}
