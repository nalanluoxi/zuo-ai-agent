package com.example.zuoaiagent.trace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.trace.entity.RagTraceNodeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 节点追踪 Mapper
 */
@Mapper
public interface RagTraceNodeMapper extends BaseMapper<RagTraceNodeDO> {
}
