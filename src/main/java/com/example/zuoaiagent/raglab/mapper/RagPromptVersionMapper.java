package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.raglab.entity.RagPromptVersionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG Prompt 版本 Mapper
 */
@Mapper
public interface RagPromptVersionMapper extends BaseMapper<RagPromptVersionDO> {
}
