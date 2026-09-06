package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.raglab.entity.RagConfigDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 配置 Mapper
 */
@Mapper
public interface RagConfigMapper extends BaseMapper<RagConfigDO> {
}
