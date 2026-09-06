package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.raglab.entity.GrayReleasePlanDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 灰度发布计划 Mapper
 */
@Mapper
public interface GrayReleasePlanMapper extends BaseMapper<GrayReleasePlanDO> {
}
