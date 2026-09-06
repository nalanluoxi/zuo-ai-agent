package com.example.zuoaiagent.raglab.service;

import com.example.zuoaiagent.raglab.entity.GrayReleasePlanDO;

import java.util.List;

/**
 * 灰度发布计划服务
 */
public interface GrayReleasePlanService {

    /**
     * 创建发布计划
     */
    GrayReleasePlanDO createPlan(GrayReleasePlanDO plan);

    /**
     * 根据 ID 查询发布计划
     */
    GrayReleasePlanDO getById(Long id);

    /**
     * 查询所有发布计划
     */
    List<GrayReleasePlanDO> listPlans();

    /**
     * 按组件类型查询发布计划
     */
    List<GrayReleasePlanDO> listByComponent(String componentType);

    /**
     * 查询组件当前正在灰度中的发布计划
     */
    GrayReleasePlanDO findActivePlan(String componentType, Long componentId);

    /**
     * 审批通过
     */
    GrayReleasePlanDO approve(Long planId, Long approvedBy);

    /**
     * 审批拒绝
     */
    GrayReleasePlanDO reject(Long planId, Long approvedBy);

    /**
     * 开始灰度（APPROVED → GRAYING）
     */
    GrayReleasePlanDO startGray(Long planId);

    /**
     * 全量发布（GRAYING → ACTIVE）
     */
    GrayReleasePlanDO activate(Long planId);

    /**
     * 回滚（GRAYING/ACTIVE → ROLLED_BACK）
     * 会真正恢复组件到发布前的版本（fromVersionId）
     */
    GrayReleasePlanDO rollback(Long planId);

    /**
     * 判断用户是否命中灰度
     */
    boolean isUserInGray(Long userId, String componentType, Long componentId);

    /**
     * 获取用户应使用的版本 ID（灰度版本 or 原始版本）
     */
    Long resolveVersionId(Long userId, String componentType, Long componentId, Long currentVersionId);

    /**
     * 记录灰度指标快照
     */
    void updateMetricsSnapshot(Long planId, String metricsJson);
}
