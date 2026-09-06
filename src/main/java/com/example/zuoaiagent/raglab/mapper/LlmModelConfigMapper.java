package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * LLM 模型配置 Mapper
 */
@Mapper
public interface LlmModelConfigMapper extends BaseMapper<LlmModelConfigDO> {
}
