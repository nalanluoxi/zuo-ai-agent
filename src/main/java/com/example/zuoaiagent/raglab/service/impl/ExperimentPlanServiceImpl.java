package com.example.zuoaiagent.raglab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.raglab.entity.RagExperimentPlanDO;
import com.example.zuoaiagent.raglab.entity.RagPlanCustomQuestionDO;
import com.example.zuoaiagent.raglab.entity.RagPlanQuestionRefDO;
import com.example.zuoaiagent.raglab.mapper.RagExperimentPlanMapper;
import com.example.zuoaiagent.raglab.mapper.RagPlanCustomQuestionMapper;
import com.example.zuoaiagent.raglab.mapper.RagPlanQuestionRefMapper;
import com.example.zuoaiagent.raglab.service.ExperimentPlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实验计划服务实现
 */
@Service
public class ExperimentPlanServiceImpl implements ExperimentPlanService {

    private static final Logger log = LoggerFactory.getLogger(ExperimentPlanServiceImpl.class);

    private final RagExperimentPlanMapper planMapper;
    private final RagPlanQuestionRefMapper questionRefMapper;
    private final RagPlanCustomQuestionMapper customQuestionMapper;

    public ExperimentPlanServiceImpl(RagExperimentPlanMapper planMapper,
                                     RagPlanQuestionRefMapper questionRefMapper,
                                     RagPlanCustomQuestionMapper customQuestionMapper) {
        this.planMapper = planMapper;
        this.questionRefMapper = questionRefMapper;
        this.customQuestionMapper = customQuestionMapper;
    }

    @Override
    @Transactional
    public RagExperimentPlanDO createPlan(RagExperimentPlanDO plan, List<Long> questionIds,
                                          List<RagPlanCustomQuestionDO> customQuestions) {
        plan.setStatus("PENDING");
        plan.setCreateTime(new Date());
        plan.setUpdateTime(new Date());
        planMapper.insert(plan);

        // 插入全局题库引用
        if (questionIds != null && !questionIds.isEmpty()) {
            for (Long questionId : questionIds) {
                RagPlanQuestionRefDO ref = new RagPlanQuestionRefDO();
                ref.setPlanId(plan.getId());
                ref.setQuestionId(questionId);
                questionRefMapper.insert(ref);
            }
        }

        // 插入自定义提问
        if (customQuestions != null && !customQuestions.isEmpty()) {
            for (RagPlanCustomQuestionDO cq : customQuestions) {
                cq.setPlanId(plan.getId());
                cq.setCreateTime(new Date());
                customQuestionMapper.insert(cq);
            }
        }

        log.info("[实验计划] 创建计划: id={}, name={}", plan.getId(), plan.getPlanName());
        return plan;
    }

    @Override
    public RagExperimentPlanDO getById(Long id) {
        return planMapper.selectById(id);
    }

    @Override
    public List<RagExperimentPlanDO> listAll() {
        return planMapper.selectList(
                new LambdaQueryWrapper<RagExperimentPlanDO>()
                        .orderByDesc(RagExperimentPlanDO::getCreateTime)
        );
    }

    @Override
    public RagExperimentPlanDO executePlan(Long planId) {
        RagExperimentPlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("实验计划不存在: " + planId);
        }
        if (!"PENDING".equals(plan.getStatus())) {
            throw new IllegalStateException("只有 PENDING 状态可以执行，当前状态: " + plan.getStatus());
        }

        plan.setStatus("RUNNING");
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);

        // TODO: 异步执行实验（调用评估引擎）
        // 当前阶段仅更新状态，后续接入 RagEvaluationService
        log.info("[实验计划] 开始执行计划: id={}", planId);

        return plan;
    }

    @Override
    public List<Long> getPlanQuestionIds(Long planId) {
        List<RagPlanQuestionRefDO> refs = questionRefMapper.selectList(
                new LambdaQueryWrapper<RagPlanQuestionRefDO>()
                        .eq(RagPlanQuestionRefDO::getPlanId, planId)
        );
        return refs.stream()
                .map(RagPlanQuestionRefDO::getQuestionId)
                .collect(Collectors.toList());
    }

    @Override
    public List<RagPlanCustomQuestionDO> getPlanCustomQuestions(Long planId) {
        return customQuestionMapper.selectList(
                new LambdaQueryWrapper<RagPlanCustomQuestionDO>()
                        .eq(RagPlanCustomQuestionDO::getPlanId, planId)
        );
    }

    @Override
    @Transactional
    public void deletePlan(Long planId) {
        planMapper.deleteById(planId);
        questionRefMapper.delete(
                new LambdaQueryWrapper<RagPlanQuestionRefDO>()
                        .eq(RagPlanQuestionRefDO::getPlanId, planId)
        );
        customQuestionMapper.delete(
                new LambdaQueryWrapper<RagPlanCustomQuestionDO>()
                        .eq(RagPlanCustomQuestionDO::getPlanId, planId)
        );
    }
}
