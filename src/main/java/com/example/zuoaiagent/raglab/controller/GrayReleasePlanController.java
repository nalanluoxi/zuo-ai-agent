package com.example.zuoaiagent.raglab.controller;

import com.example.zuoaiagent.raglab.entity.GrayReleasePlanDO;
import com.example.zuoaiagent.raglab.entity.GrayReleasePlanVO;
import com.example.zuoaiagent.raglab.entity.LlmModelConfigDO;
import com.example.zuoaiagent.raglab.mapper.GrayReleasePlanModelMapper;
import com.example.zuoaiagent.raglab.mapper.LlmModelConfigMapper;
import com.example.zuoaiagent.raglab.service.GrayReleasePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 灰度发布计划控制器
 */
@RestController
@RequestMapping("/rag-lab/gray-release")
@Tag(name = "灰度发布计划", description = "灰度发布计划管理 API")
public class GrayReleasePlanController {

    private final GrayReleasePlanService grayReleasePlanService;
    private final GrayReleasePlanModelMapper planModelMapper;
    private final LlmModelConfigMapper llmModelConfigMapper;

    public GrayReleasePlanController(GrayReleasePlanService grayReleasePlanService,
                                      GrayReleasePlanModelMapper planModelMapper,
                                      LlmModelConfigMapper llmModelConfigMapper) {
        this.grayReleasePlanService = grayReleasePlanService;
        this.planModelMapper = planModelMapper;
        this.llmModelConfigMapper = llmModelConfigMapper;
    }

    @PostMapping
    @Operation(summary = "创建发布计划")
    public GrayReleasePlanDO createPlan(@RequestBody GrayReleasePlanDO plan) {
        return grayReleasePlanService.createPlan(plan);
    }

    @PostMapping("/model")
    @Operation(summary = "创建模型灰度发布计划（批量）")
    public GrayReleasePlanDO createModelPlan(@RequestBody CreateModelPlanRequest request) {
        return grayReleasePlanService.createModelPlan(request.getPlan(), request.getModelConfigIds());
    }

    public static class CreateModelPlanRequest {
        private GrayReleasePlanDO plan;
        private List<Long> modelConfigIds;

        public GrayReleasePlanDO getPlan() { return plan; }
        public void setPlan(GrayReleasePlanDO plan) { this.plan = plan; }
        public List<Long> getModelConfigIds() { return modelConfigIds; }
        public void setModelConfigIds(List<Long> modelConfigIds) { this.modelConfigIds = modelConfigIds; }
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询发布计划")
    public GrayReleasePlanDO getPlan(@PathVariable Long id) {
        return grayReleasePlanService.getById(id);
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有发布计划")
    public List<GrayReleasePlanVO> listPlans() {
        List<GrayReleasePlanDO> plans = grayReleasePlanService.listPlans();
        return plans.stream().map(plan -> {
            GrayReleasePlanVO vo = new GrayReleasePlanVO();
            // 复制属性
            vo.setId(plan.getId());
            vo.setPlanName(plan.getPlanName());
            vo.setComponentType(plan.getComponentType());
            vo.setComponentId(plan.getComponentId());
            vo.setFromVersionId(plan.getFromVersionId());
            vo.setToVersionId(plan.getToVersionId());
            vo.setGrayRatio(plan.getGrayRatio());
            vo.setGrayMode(plan.getGrayMode());
            vo.setGrayUserIds(plan.getGrayUserIds());
            vo.setGrayDurationHours(plan.getGrayDurationHours());
            vo.setStatus(plan.getStatus());
            vo.setApprovedBy(plan.getApprovedBy());
            vo.setApprovedTime(plan.getApprovedTime());
            vo.setGrayMetricsSnapshot(plan.getGrayMetricsSnapshot());
            vo.setChangeLog(plan.getChangeLog());
            vo.setCreateUserId(plan.getCreateUserId());
            vo.setCreateTime(plan.getCreateTime());
            vo.setUpdateTime(plan.getUpdateTime());
            vo.setFinishTime(plan.getFinishTime());

            // 填充模型名称
            if ("MODEL".equals(plan.getComponentType())) {
                List<Long> modelIds = planModelMapper.selectModelConfigIdsByPlanId(plan.getId());
                if (!modelIds.isEmpty()) {
                    List<LlmModelConfigDO> models = llmModelConfigMapper.selectBatchIds(modelIds);
                    String names = models.stream()
                            .map(LlmModelConfigDO::getModelName)
                            .collect(Collectors.joining(", "));
                    vo.setModelNames(names);
                }
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @GetMapping("/list/{componentType}")
    @Operation(summary = "按组件类型查询发布计划")
    public List<GrayReleasePlanDO> listByComponent(@PathVariable String componentType) {
        return grayReleasePlanService.listByComponent(componentType);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "审批通过")
    public GrayReleasePlanDO approve(@PathVariable Long id, @RequestParam Long approvedBy) {
        return grayReleasePlanService.approve(id, approvedBy);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "审批拒绝")
    public GrayReleasePlanDO reject(@PathVariable Long id, @RequestParam Long approvedBy) {
        return grayReleasePlanService.reject(id, approvedBy);
    }

    @PostMapping("/{id}/start-gray")
    @Operation(summary = "开始灰度")
    public GrayReleasePlanDO startGray(@PathVariable Long id) {
        return grayReleasePlanService.startGray(id);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "全量发布")
    public GrayReleasePlanDO activate(@PathVariable Long id) {
        return grayReleasePlanService.activate(id);
    }

    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚")
    public GrayReleasePlanDO rollback(@PathVariable Long id) {
        return grayReleasePlanService.rollback(id);
    }

    @GetMapping("/{id}/active")
    @Operation(summary = "查询组件当前灰度中的发布计划")
    public GrayReleasePlanDO findActivePlan(
            @PathVariable Long id,
            @RequestParam String componentType) {
        return grayReleasePlanService.findActivePlan(componentType, id);
    }

    @GetMapping("/{id}/user-in-gray")
    @Operation(summary = "判断用户是否命中灰度")
    public boolean isUserInGray(
            @PathVariable Long id,
            @RequestParam Long userId,
            @RequestParam String componentType) {
        return grayReleasePlanService.isUserInGray(userId, componentType, id);
    }

    @PostMapping("/{id}/metrics")
    @Operation(summary = "更新灰度指标快照")
    public void updateMetrics(@PathVariable Long id, @RequestParam String metricsJson) {
        grayReleasePlanService.updateMetricsSnapshot(id, metricsJson);
    }
}
