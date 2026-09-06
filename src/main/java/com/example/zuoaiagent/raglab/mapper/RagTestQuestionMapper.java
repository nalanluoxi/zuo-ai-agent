package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.raglab.entity.RagTestQuestionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 测试问题 Mapper
 */
@Mapper
public interface RagTestQuestionMapper extends BaseMapper<RagTestQuestionDO> {
}
