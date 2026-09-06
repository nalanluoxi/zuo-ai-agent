package com.example.zuoaiagent.raglab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * RAG 配置版本（对应 t_rag_config_version）
 */
@TableName("t_rag_config_version")
public class RagConfigVersionDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置 ID */
    private Long configId;

    /** 版本号 */
    private Integer versionNo;

    /** 快照数据（JSON） */
    private String snapshotData;

    /** 变更日志 */
    private String changeLog;

    /** 创建时间 */
    private Date createTime;

    /** 创建用户 ID */
    private Long createUserId;

    /** 租户 ID */
    private Long tenantId;

    public RagConfigVersionDO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConfigId() { return configId; }
    public void setConfigId(Long configId) { this.configId = configId; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public String getSnapshotData() { return snapshotData; }
    public void setSnapshotData(String snapshotData) { this.snapshotData = snapshotData; }

    public String getChangeLog() { return changeLog; }
    public void setChangeLog(String changeLog) { this.changeLog = changeLog; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Long getCreateUserId() { return createUserId; }
    public void setCreateUserId(Long createUserId) { this.createUserId = createUserId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
