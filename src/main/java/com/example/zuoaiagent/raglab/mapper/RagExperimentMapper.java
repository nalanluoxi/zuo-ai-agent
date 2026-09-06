package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.raglab.entity.RagExperimentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 实验 Mapper
 */
@Mapper
public interface RagExperimentMapper extends BaseMapper<RagExperimentDO> {
}
