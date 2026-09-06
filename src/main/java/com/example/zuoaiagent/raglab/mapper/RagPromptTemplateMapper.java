package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.raglab.entity.RagPromptTemplateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG Prompt 模板 Mapper
 */
@Mapper
public interface RagPromptTemplateMapper extends BaseMapper<RagPromptTemplateDO> {
}
