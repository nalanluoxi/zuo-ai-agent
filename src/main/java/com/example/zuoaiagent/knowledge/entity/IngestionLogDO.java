package com.example.zuoaiagent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 入库日志实体
 */
@Data
@TableName("t_ingestion_log")
public class IngestionLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;
    private Long kbId;
    private String stage;
    private String status;
    private Integer durationMs;
    private Integer totalDurationMs;
    private Integer chunksCount;
    private Integer chunksSuccess;
    private String errorMessage;
    private String fileType;
    private Long fileSize;
    private Integer textLength;
    private String strategy;

    private Date createdAt;
}
