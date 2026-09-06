package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.raglab.entity.RagTestDocumentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 测试文档 Mapper
 */
@Mapper
public interface RagTestDocumentMapper extends BaseMapper<RagTestDocumentDO> {
}
