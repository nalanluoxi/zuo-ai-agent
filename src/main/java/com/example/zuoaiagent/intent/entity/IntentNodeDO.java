package com.example.zuoaiagent.intent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 意图节点实体（对应 t_intent_node 表）
 *
 * <p>三级层次结构：
 * <ul>
 *   <li>level=1: domain（领域，如"金融"）</li>
 *   <li>level=2: category（类目，如"股票投资"）</li>
 *   <li>level=3: topic（话题，叶子节点，关联知识库）</li>
 * </ul>
 */
@TableName("t_intent_node")
public class IntentNodeDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父节点 ID，NULL 表示顶级节点 */
    private Long parentId;

    /** 节点名称，如"金融"、"股票投资" */
    private String label;

    /** 节点描述，用于 LLM 分类 Prompt */
    private String description;

    /** 层级：1=domain, 2=category, 3=topic */
    private Short level;

    /** 是否系统/闲聊节点：1=是（直接短路，不走向量检索），0=否 */
    private Short isSystem;

    /** 关联知识库 ID（topic 级别才填） */
    private Long kbId;

    /** 排序权重 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Short deleted;

    public IntentNodeDO() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Short getLevel() { return level; }
    public void setLevel(Short level) { this.level = level; }

    public Short getIsSystem() { return isSystem; }
    public void setIsSystem(Short isSystem) { this.isSystem = isSystem; }

    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public Short getDeleted() { return deleted; }
    public void setDeleted(Short deleted) { this.deleted = deleted; }
}
