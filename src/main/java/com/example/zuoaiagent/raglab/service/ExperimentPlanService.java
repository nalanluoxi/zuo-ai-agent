package com.example.zuoaiagent.raglab.service;

import com.example.zuoaiagent.raglab.entity.RagExperimentPlanDO;
import com.example.zuoaiagent.raglab.entity.RagPlanCustomQuestionDO;

import java.util.List;

/**
 * 实验计划服务
 */
public interface ExperimentPlanService {

    /**
     * 创建实验计划
     * @param plan 实验计划
     * @param questionIds 关联的全局题库 ID 列表
     * @param customQuestions 自定义提问列表
     */
    RagExperimentPlanDO createPlan(RagExperimentPlanDO plan, List<Long> questionIds,
                                   List<RagPlanCustomQuestionDO> customQuestions);

    RagExperimentPlanDO getById(Long id);

    List<RagExperimentPlanDO> listAll();

    /** 执行实验计划 */
    RagExperimentPlanDO executePlan(Long planId);

    /** 获取计划关联的全局题库 ID 列表 */
    List<Long> getPlanQuestionIds(Long planId);

    /** 获取计划关联的自定义提问列表 */
    List<RagPlanCustomQuestionDO> getPlanCustomQuestions(Long planId);

    /** 删除实验计划 */
    void deletePlan(Long planId);
}
