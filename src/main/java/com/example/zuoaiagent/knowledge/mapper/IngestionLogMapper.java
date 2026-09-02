package com.example.zuoaiagent.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.knowledge.entity.IngestionLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IngestionLogMapper extends BaseMapper<IngestionLogDO> {
}
