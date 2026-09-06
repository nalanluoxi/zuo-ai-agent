package com.example.zuoaiagent.raglab.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.zuoaiagent.raglab.entity.GrayReleasePlanDO;
import com.example.zuoaiagent.raglab.mapper.GrayReleasePlanMapper;
import com.example.zuoaiagent.raglab.service.GrayReleasePlanService;
import com.example.zuoaiagent.raglab.service.RagConfigService;
import com.example.zuoaiagent.raglab.service.RagPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 灰度发布计划服务实现
 */
@Service
public class GrayReleasePlanServiceImpl implements GrayReleasePlanService {

    private static final Logger log = LoggerFactory.getLogger(GrayReleasePlanServiceImpl.class);

    private final GrayReleasePlanMapper planMapper;
    private final RagConfigService ragConfigService;
    private final RagPromptService ragPromptService;

    public GrayReleasePlanServiceImpl(GrayReleasePlanMapper planMapper,
                                       RagConfigService ragConfigService,
                                       RagPromptService ragPromptService) {
        this.planMapper = planMapper;
        this.ragConfigService = ragConfigService;
        this.ragPromptService = ragPromptService;
    }

    @Override
    public GrayReleasePlanDO createPlan(GrayReleasePlanDO plan) {
        plan.setStatus("DRAFT");
        plan.setCreateTime(new Date());
        plan.setUpdateTime(new Date());
        planMapper.insert(plan);
        return plan;
    }

    @Override
    public GrayReleasePlanDO getById(Long id) {
        return planMapper.selectById(id);
    }

    @Override
    public List<GrayReleasePlanDO> listPlans() {
        return planMapper.selectList(
                new LambdaQueryWrapper<GrayReleasePlanDO>()
                        .orderByDesc(GrayReleasePlanDO::getCreateTime)
        );
    }

    @Override
    public List<GrayReleasePlanDO> listByComponent(String componentType) {
        return planMapper.selectList(
                new LambdaQueryWrapper<GrayReleasePlanDO>()
                        .eq(GrayReleasePlanDO::getComponentType, componentType)
                        .orderByDesc(GrayReleasePlanDO::getCreateTime)
        );
    }

    @Override
    public GrayReleasePlanDO findActivePlan(String componentType, Long componentId) {
        // 查找正在灰度中的计划（GRAYING 状态）
        return planMapper.selectOne(
                new LambdaQueryWrapper<GrayReleasePlanDO>()
                        .eq(GrayReleasePlanDO::getComponentType, componentType)
                        .eq(GrayReleasePlanDO::getComponentId, componentId)
                        .eq(GrayReleasePlanDO::getStatus, "GRAYING")
                        .last("LIMIT 1")
        );
    }

    @Override
    public GrayReleasePlanDO approve(Long planId, Long approvedBy) {
        GrayReleasePlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("发布计划不存在: " + planId);
        }
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new IllegalStateException("只有草稿状态可以审批，当前状态: " + plan.getStatus());
        }

        plan.setStatus("APPROVED");
        plan.setApprovedBy(approvedBy);
        plan.setApprovedTime(new Date());
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);
        log.info("[灰度发布] 计划 {} 审批通过，审批人: {}", planId, approvedBy);
        return plan;
    }

    @Override
    public GrayReleasePlanDO reject(Long planId, Long approvedBy) {
        GrayReleasePlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("发布计划不存在: " + planId);
        }
        if (!"DRAFT".equals(plan.getStatus())) {
            throw new IllegalStateException("只有草稿状态可以拒绝，当前状态: " + plan.getStatus());
        }

        plan.setStatus("REJECTED");
        plan.setApprovedBy(approvedBy);
        plan.setApprovedTime(new Date());
        plan.setFinishTime(new Date());
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);
        log.info("[灰度发布] 计划 {} 审批拒绝，审批人: {}", planId, approvedBy);
        return plan;
    }

    @Override
    public GrayReleasePlanDO startGray(Long planId) {
        GrayReleasePlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("发布计划不存在: " + planId);
        }
        if (!"APPROVED".equals(plan.getStatus())) {
            throw new IllegalStateException("只有审批通过状态可以开始灰度，当前状态: " + plan.getStatus());
        }

        plan.setStatus("GRAYING");
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);
        log.info("[灰度发布] 计划 {} 开始灰度，比例: {}, 模式: {}",
                planId, plan.getGrayRatio(), plan.getGrayMode());
        return plan;
    }

    @Override
    public GrayReleasePlanDO activate(Long planId) {
        GrayReleasePlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("发布计划不存在: " + planId);
        }
        if (!"GRAYING".equals(plan.getStatus())) {
            throw new IllegalStateException("只有灰度中状态可以全量发布，当前状态: " + plan.getStatus());
        }

        plan.setStatus("ACTIVE");
        plan.setFinishTime(new Date());
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);
        log.info("[灰度发布] 计划 {} 全量发布完成", planId);
        return plan;
    }

    @Override
    @Transactional
    public GrayReleasePlanDO rollback(Long planId) {
        GrayReleasePlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("发布计划不存在: " + planId);
        }
        // 支持 GRAYING 和 ACTIVE 状态的回滚
        if (!"GRAYING".equals(plan.getStatus()) && !"ACTIVE".equals(plan.getStatus())) {
            throw new IllegalStateException("只有灰度中或已激活状态可以回滚，当前状态: " + plan.getStatus());
        }

        // 真正执行回滚：恢复到发布前的版本
        Long fromVersionId = plan.getFromVersionId();
        String componentType = plan.getComponentType();
        Long componentId = plan.getComponentId();

        try {
            if (fromVersionId != null) {
                if ("CONFIG".equals(componentType)) {
                    // 配置回滚：恢复到发布前的版本
                    ragConfigService.rollbackToVersion(componentId, fromVersionId);
                    log.info("[灰度发布] 配置回滚成功: componentId={}, fromVersionId={}", componentId, fromVersionId);
                } else if ("PROMPT".equals(componentType)) {
                    // 提示词回滚：恢复到发布前的版本
                    ragPromptService.rollbackToVersion(componentId, fromVersionId);
                    log.info("[灰度发布] 提示词回滚成功: componentId={}, fromVersionId={}", componentId, fromVersionId);
                } else if ("MODEL".equals(componentType)) {
                    // 模型配置暂无版本管理，仅标记计划状态
                    log.info("[灰度发布] 模型配置无版本管理，仅标记计划为回滚状态: planId={}", planId);
                }
            } else {
                log.warn("[灰度发布] 发布计划没有 fromVersionId，无法回滚到发布前版本: planId={}", planId);
            }
        } catch (Exception e) {
            log.error("[灰度发布] 回滚组件失败: planId={}, error={}", planId, e.getMessage(), e);
            throw new IllegalStateException("回滚失败: " + e.getMessage(), e);
        }

        plan.setStatus("ROLLED_BACK");
        plan.setFinishTime(new Date());
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);
        log.info("[灰度发布] 计划 {} 已回滚到版本 {}", planId, fromVersionId);
        return plan;
    }

    @Override
    public boolean isUserInGray(Long userId, String componentType, Long componentId) {
        GrayReleasePlanDO plan = findActivePlan(componentType, componentId);
        if (plan == null) {
            return false;
        }

        String mode = plan.getGrayMode();
        if (mode == null) mode = "PERCENT";

        switch (mode) {
            case "PERCENT":
                return checkPercent(userId, plan.getGrayRatio());
            case "LIST":
                return checkList(userId, plan.getGrayUserIds());
            case "BOTH":
                return checkPercent(userId, plan.getGrayRatio()) || checkList(userId, plan.getGrayUserIds());
            default:
                return false;
        }
    }

    @Override
    public Long resolveVersionId(Long userId, String componentType, Long componentId, Long currentVersionId) {
        if (isUserInGray(userId, componentType, componentId)) {
            GrayReleasePlanDO plan = findActivePlan(componentType, componentId);
            if (plan != null) {
                return plan.getToVersionId();
            }
        }
        return currentVersionId;
    }

    @Override
    public void updateMetricsSnapshot(Long planId, String metricsJson) {
        GrayReleasePlanDO plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("发布计划不存在: " + planId);
        }
        plan.setGrayMetricsSnapshot(metricsJson);
        plan.setUpdateTime(new Date());
        planMapper.updateById(plan);
    }

    /**
     * 百分比检查：userId hash 取模
     */
    private boolean checkPercent(Long userId, Double ratio) {
        if (ratio == null || ratio <= 0) return false;
        if (ratio >= 1.0) return true;
        if (userId == null) return false;

        // userId hash 取模，确定性分配
        int hash = Math.abs(userId.hashCode());
        double percentile = (hash % 100) / 100.0;
        return percentile < ratio;
    }

    /**
     * 名单检查：userId 在灰度名单中
     */
    private boolean checkList(Long userId, String grayUserIds) {
        if (grayUserIds == null || grayUserIds.isBlank() || userId == null) return false;

        return Arrays.stream(grayUserIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(id -> {
                    try {
                        return Long.parseLong(id) == userId;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
    }
}
