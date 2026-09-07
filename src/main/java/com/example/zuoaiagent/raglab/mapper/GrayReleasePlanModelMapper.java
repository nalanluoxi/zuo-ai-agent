package com.example.zuoaiagent.raglab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.raglab.entity.GrayReleasePlanModelDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GrayReleasePlanModelMapper extends BaseMapper<GrayReleasePlanModelDO> {

    @Select("SELECT model_config_id FROM t_gray_release_plan_model WHERE plan_id = #{planId}")
    List<Long> selectModelConfigIdsByPlanId(Long planId);

    default List<GrayReleasePlanModelDO> selectByPlanId(Long planId) {
        return selectList(new LambdaQueryWrapper<GrayReleasePlanModelDO>()
                .eq(GrayReleasePlanModelDO::getPlanId, planId));
    }
}
