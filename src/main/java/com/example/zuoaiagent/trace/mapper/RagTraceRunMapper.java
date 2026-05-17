package com.example.zuoaiagent.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.trace.entity.RagTraceRunDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 流水线追踪 Mapper
 */
@Mapper
public interface RagTraceRunMapper extends BaseMapper<RagTraceRunDO> {
}
