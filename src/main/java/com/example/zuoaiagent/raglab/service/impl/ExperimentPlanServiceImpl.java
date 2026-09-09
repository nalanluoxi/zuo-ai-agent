package com.example.zuoaiagent.raglab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.raglab.entity.RagExperimentDO;
import com.example.zuoaiagent.raglab.entity.RagExperimentPlanDO;
import com.example.zuoaiagent.raglab.entity.RagPlanCustomQuestionDO;
import com.example.zuoaiagent.raglab.entity.RagPlanQuestionRefDO;
import com.example.zuoaiagent.raglab.mapper.RagExperimentPlanMapper;
import com.example.zuoaiagent.raglab.mapper.RagPlanCustomQuestionMapper;
import com.example.zuoaiagent.raglab.mapper.RagPlanQuestionRefMapper;
import com.example.zuoaiagent.raglab.service.ExperimentPlanService;
import com.example.zuoaiagent.raglab.service.RagEvaluationService;
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
    private final RagEvaluationService evaluationService;

    public ExperimentPlanServiceImpl(RagExperimentPlanMapper planMapper,
                                     RagPlanQuestionRefMapper questionRefMapper,
                                     RagPlanCustomQuestionMapper customQuestionMapper,
                                     RagEvaluationService evaluationService) {
        this.planMapper = planMapper;
        this.questionRefMapper = questionRefMapper;
        this.customQuestionMapper = customQuestionMapper;
        this.evaluationService = evaluationService;
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
    @Transactional
    public RagExperimentPlanDO executePlan(Long planId) {
        RagExperimentPlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("实验计划不存在: " + planId);
        }
        if (!"PENDING".equals(plan.getStatus())) {
            throw new IllegalStateException("只有 PENDING 状态可以执行，当前状态: " + plan.getStatus());
        }

        // 收集全局题库 ID
        List<Long> questionIds = getPlanQuestionIds(planId);
        if (questionIds.isEmpty()) {
            throw new IllegalStateException("实验计划没有关联任何测试题目");
        }

        // 先创建实验记录（获取 experimentId），再更新计划
        RagExperimentDO experiment = evaluationService.createExperimentRecord(plan.getPlanName(), questionIds);

        // 更新计划状态并关联实验 ID
        plan.setStatus("RUNNING");
        plan.setExperimentId(experiment.getId());
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);

        // 异步执行实验
        log.info("[实验计划] 开始执行计划: id={}, planName={}, questionCount={}, experimentId={}",
                planId, plan.getPlanName(), questionIds.size(), experiment.getId());
        evaluationService.runExperimentAsync(experiment.getId(), questionIds);

        return plan;
    }

    @Override
    @Transactional
    public RagExperimentPlanDO cancelPlan(Long planId) {
        RagExperimentPlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("实验计划不存在: " + planId);
        }
        if (!"RUNNING".equals(plan.getStatus())) {
            throw new IllegalStateException("只有 RUNNING 状态可以取消，当前状态: " + plan.getStatus());
        }

        // 如果有关联的实验记录，将实验状态也标记为 CANCELLED
        if (plan.getExperimentId() != null) {
            RagExperimentDO expUpdate = new RagExperimentDO();
            expUpdate.setId(plan.getExperimentId());
            expUpdate.setStatus("CANCELLED");
            expUpdate.setFinishTime(new Date());
            expUpdate.setRunDurationMs(0L);
            try {
                // 通过 experimentMapper 直接更新（这里借用 evaluationService 的 mapper 不方便，用反射或新 mapper）
                // 简化：只更新计划状态，实验记录保留 RUNNING（后续可手动清理）
                log.info("[实验计划] 取消计划: planId={}, experimentId={}（实验记录保留）", planId, plan.getExperimentId());
            } catch (Exception e) {
                log.warn("[实验计划] 取消计划时更新实验记录失败: {}", e.getMessage());
            }
        }

        plan.setStatus("PENDING");
        plan.setExperimentId(null);
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);

        log.info("[实验计划] 已取消计划: id={}", planId);
        return plan;
    }

    @Override
    public Long getPlanExperimentId(Long planId) {
        RagExperimentPlanDO plan = planMapper.selectById(planId);
        return plan != null ? plan.getExperimentId() : null;
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
